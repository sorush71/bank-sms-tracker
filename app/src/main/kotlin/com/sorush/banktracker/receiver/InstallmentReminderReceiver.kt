package com.sorush.banktracker.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sorush.banktracker.MainActivity
import com.sorush.banktracker.data.DatabaseHelper
import com.sorush.banktracker.util.JalaliCalendar
import java.text.NumberFormat
import java.util.Locale

class InstallmentReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "installment_reminders_channel"
        const val CHANNEL_NAME = "یادآور اقساط و بدهی‌ها"

        fun checkAndTriggerReminders(context: Context) {
            val db = DatabaseHelper.getInstance(context)
            val all = db.getAllInstallments()
            if (all.isEmpty()) return

            val now = JalaliCalendar.now()
            val todayDay = now.day

            for (inst in all) {
                // If tomorrow is due day:
                // Normal case: dueDay == todayDay + 1
                // Month rollover: today is 30 or 31 and dueDay is 1
                val isDueTomorrow = (inst.dueDay == todayDay + 1) ||
                        ((todayDay >= 29) && inst.dueDay == 1)

                if (isDueTomorrow) {
                    showReminderNotification(context, inst.title, inst.bankName, inst.amountToman, inst.id.toInt())
                }
            }
        }

        private fun showReminderNotification(
            context: Context,
            title: String,
            bankName: String,
            amountToman: Long,
            notificationId: Int
        ) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "اعلان یادآوری سررسید اقساط یک روز قبل از موعد"
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val formattedAmount = NumberFormat.getNumberInstance(Locale.US).format(amountToman)

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("🔔 یادآور قسط: فردا موعد «$title» است")
                .setContentText("مبلغ $formattedAmount تومان | حساب مقصد: $bankName (لطفاً موجودی را تامین کنید)")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "فردا سررسید قسط «$title» به مبلغ $formattedAmount تومان می‌باشد.\n" +
                        "لطفاً مطمئن شوید که این مبلغ در حساب $bankName موجود است تا کسر شود."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            nm.notify(notificationId + 200000, notification)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        checkAndTriggerReminders(context)
    }
}
