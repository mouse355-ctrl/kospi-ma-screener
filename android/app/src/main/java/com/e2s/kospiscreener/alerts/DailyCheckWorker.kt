package com.e2s.kospiscreener.alerts

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.e2s.kospiscreener.MainActivity
import com.e2s.kospiscreener.R
import com.e2s.kospiscreener.ScreenerApp
import com.e2s.kospiscreener.data.LatestPayload
import com.e2s.kospiscreener.data.Repository
import com.e2s.kospiscreener.data.Settings
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * 하루 한 번(장 마감 후) latest.json 을 읽어, 새 기준일에 신규 진입 종목이 있으면 알림을 띄웁니다.
 * 서버 푸시(Firebase) 없이 앱 스스로 확인하는 방식.
 */
class DailyCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val settings = Settings(applicationContext)
        if (!settings.isConfigured) return Result.success()
        return try {
            val payload = Repository(settings).loadLatestBlocking()
            checkAndNotify(applicationContext, settings, payload)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "일일 확인 실패", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "DailyCheck"
        private const val WORK_NAME = "daily-screen-check"
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")

        /** 매일 17:00 KST 무렵 시작하는 24시간 주기 작업 등록 (이미 있으면 유지) */
        fun schedule(ctx: Context) {
            val now = ZonedDateTime.now(KST)
            var next = now.with(LocalTime.of(17, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next)

            val req = PeriodicWorkRequestBuilder<DailyCheckWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /** 앱을 직접 열어 새 데이터를 받았을 때도 같은 규칙으로 알림 (중복 방지 포함) */
        fun checkAndNotify(ctx: Context, settings: Settings, payload: LatestPayload) {
            val runDate = payload.summary.runDate ?: return
            if (settings.lastNotifiedRunDate == runDate) return
            settings.lastNotifiedRunDate = runDate

            val newOnes = payload.results.filter { it.isNew }
            if (newOnes.isEmpty()) return
            val names = newOnes.map { it.name }
            val body = names.take(6).joinToString(", ") + if (names.size > 6) " 외 ${names.size - 6}개" else ""
            showNotification(ctx, "정배열 신규 진입 ${names.size}종목 ($runDate)", body)
        }

        private fun showNotification(ctx: Context, title: String, body: String) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val intent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, ScreenerApp.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(1001, n)
        }
    }
}
