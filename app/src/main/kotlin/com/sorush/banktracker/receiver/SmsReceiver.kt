package com.sorush.banktracker.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.sorush.banktracker.data.DatabaseHelper
import com.sorush.banktracker.parser.TransactionParser
import com.sorush.banktracker.ui.popup.OverlayDialogActivity
import java.text.NumberFormat
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "bank_transactions_channel"
        const val CHANNEL_NAME = "تراکنش‌های بانکی"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: ""
        val bodyBuilder = StringBuilder()
        for (sms in messages) {
            bodyBuilder.append(sms.displayMessageBody ?: "")
        }
        val fullBody = bodyBuilder.toString()

        val db = DatabaseHelper.getInstance(context)

        // Check if sender is in the whitelist (e.g. ResalatBank, B.Pasargad)
        if (!db.isSenderWhitelisted(sender)) {
            return
        }

        // Parse the transaction
        val parsed = TransactionParser.parse(sender, fullBody) ?: return

        // 1. Launch Overlay Dialog Activity directly
        val dialogIntent = Intent(context, OverlayDialogActivity::class.java).apply {
            putExtra("EXTRA_AMOUNT_TOMAN", parsed.amountToman)
            putExtra("EXTRA_TYPE", parsed.type.name)
            putExtra("EXTRA_BANK_NAME", parsed.bankName)
            putExtra("EXTRA_RAW_MESSAGE", parsed.rawMessage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        try {
            context.startActivity(dialogIntent)
        } catch (_: Exception) {
            // In Android 14/15, background activity launch may be blocked if overlay permission isn't granted yet.
            // Fallback notification ensures user still gets the prompt immediately.
        }

        // 2. High Priority Heads-up Notification (Fallback & Status Bar Integration)
        showTransactionNotification(context, parsed.amountToman, parsed.type.titleFa, parsed.bankName, dialogIntent)
    }

    private fun showTransactionNotification(
        context: Context,
        amountToman: Long,
        typeTitle: String,
        bankName: String,
        dialogIntent: Intent
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "نمایش پاپ‌آپ و اعلان هنگام تراکنش‌های بانکی"
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            dialogIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedAmount = NumberFormat.getNumberInstance(Locale.US).format(amountToman)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("$typeTitle $bankName")
            .setContentText("مبلغ: $formattedAmount تومان - برای تعیین دسته‌بندی ضربه بزنید")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true) // Launches overlay immediately on screen
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }
}
