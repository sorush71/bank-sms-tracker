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
        const val ACTION_IGNORE_TX = "com.sorush.banktracker.ACTION_IGNORE_TX"

        fun cancelNotification(context: Context, notificationId: Int) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_IGNORE_TX) {
            val txId = intent.getLongExtra("EXTRA_TX_ID", -1L)
            if (txId != -1L) {
                val db = DatabaseHelper.getInstance(context)
                db.updateTransactionCategorization(
                    txId = txId,
                    category = "نادیده گرفته‌شده",
                    subCategory = "",
                    notes = "از اعلان نادیده گرفته شد",
                    isIgnored = true,
                    items = emptyList()
                )
                cancelNotification(context, txId.toInt())
            }
            return
        }

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

        // Save immediately as pending categorization so it is NEVER lost even if dismissed
        val txId = db.insertTransaction(
            amountToman = parsed.amountToman,
            type = parsed.type,
            category = "در انتظار تعیین",
            subCategory = "",
            bankName = parsed.bankName,
            rawMessage = parsed.rawMessage,
            isIgnored = false,
            isPending = true,
            notes = ""
        )

        // 1. Launch Overlay Dialog Activity
        val dialogIntent = Intent(context, OverlayDialogActivity::class.java).apply {
            putExtra("EXTRA_TX_ID", txId)
            putExtra("EXTRA_AMOUNT_TOMAN", parsed.amountToman)
            putExtra("EXTRA_TYPE", parsed.type.name)
            putExtra("EXTRA_BANK_NAME", parsed.bankName)
            putExtra("EXTRA_RAW_MESSAGE", parsed.rawMessage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        try {
            context.startActivity(dialogIntent)
        } catch (_: Exception) {}

        // 2. Post Persistent Notification (stays in notification drawer if popup is dismissed)
        showPersistentTransactionNotification(
            context = context,
            txId = txId,
            amountToman = parsed.amountToman,
            typeTitle = parsed.type.titleFa,
            bankName = parsed.bankName,
            dialogIntent = dialogIntent
        )
    }

    private fun showPersistentTransactionNotification(
        context: Context,
        txId: Long,
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
                description = "نمایش پاپ‌آپ و اعلان تراکنش‌ها با امکان ثبت دیرتر"
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            txId.toInt(),
            dialogIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Ignore directly from notification
        val ignoreIntent = Intent(context, SmsReceiver::class.java).apply {
            action = ACTION_IGNORE_TX
            putExtra("EXTRA_TX_ID", txId)
        }
        val ignorePendingIntent = PendingIntent.getBroadcast(
            context,
            (txId + 500000).toInt(),
            ignoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedAmount = NumberFormat.getNumberInstance(Locale.US).format(amountToman)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("💳 $typeTitle جدید $bankName: $formattedAmount تومان")
            .setContentText("این تراکنش نیاز به تعیین دسته‌بندی دارد (ضربه بزنید)")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "مبلغ: $formattedAmount تومان\nبانک: $bankName\n" +
                    "اگر در لحظه دریافت فرصت نکردید، می‌توانید همینجا دسته و جزئیات خرید را مشخص کنید."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_input_add, "تعیین دسته‌بندی", pendingIntent)
            .addAction(android.R.drawable.ic_delete, "نادیده گرفتن", ignorePendingIntent)
            .setOngoing(false) // Dismissible by user if swiped, but stays until acted upon or cleared
            .setAutoCancel(true)
            .build()

        nm.notify(txId.toInt(), notification)
    }
}
