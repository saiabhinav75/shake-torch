package com.saiabhinavgandesree.shaketorch

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory
import com.saiabhinavgandesree.shaketorch.core.Prefs

object RatingPrompt {

    private const val TOGGLES_BEFORE_ASKING = 10

    /**
     * Shows Google Play's in-app rating sheet once, after the user has used the gesture enough
     * to have an opinion. Play decides whether the sheet actually appears (it enforces a quota),
     * and never tells the app whether the user rated.
     */
    fun maybeAsk(activity: Activity, prefs: Prefs) {
        if (prefs.reviewRequested || prefs.shakeToggleCount < TOGGLES_BEFORE_ASKING) return

        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful || activity.isFinishing) return@addOnCompleteListener
            manager.launchReviewFlow(activity, request.result)
            prefs.reviewRequested = true
        }
    }

    fun openStorePage(context: Context) {
        val id = context.packageName
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")))
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id"))
            )
        }
    }
}
