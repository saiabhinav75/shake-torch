For a small free utility like this, most users will come from Play Store search and from a few well-placed community posts. The app already has a good hook to lead with: no ads, open source, fully customizable. Here's the order I'd tackle it in:

1. Get through closed testing (needed before production)
You need 12 testers opted in for 14 days in a row:
- Friends and family: add their Gmail addresses to the tester list. Each person just opts in once and keeps the app installed.
- Tester exchanges: Reddit's r/AndroidClosedTesting and r/TestersCommunity exist for this. You test other people's apps and they test yours. It's the standard way solo developers reach 12.

2. Make it findable in Play search
- App name: use your 30 characters for the search term, for example Shake Torch: Shake Flashlight (29). People search "shake flashlight", not "Shake Torch".
- Short description: it already includes "shake", "flashlight" and "no ads". Keep those words.
- Screenshots: add a one-line caption on each, such as "Shake to turn on", "Works with screen off" and "Tune the sensitivity". Captioned screenshots convert noticeably better than bare ones. I can make these once you send the original screenshots.

3. Go where your users are
The people who want this are Samsung owners who miss the gesture, and people who moved from a Motorola (which has "chop chop" for the torch) to another brand.
- Reddit: r/androidapps, r/samsung, r/GalaxyS, r/oneui, r/motorola. Write it as "I built an ad-free shake-to-torch app because the existing ones are full of ads", not as an ad. Reddit rewards genuine maker posts and punishes spam.
- XDA Forums: the Apps & Games section, and the forums for specific Samsung phones.
- Answer existing threads: search Reddit and Google for "samsung shake flashlight" and reply to old questions with a link.

4. Use the fact that it's open source
Your GitHub repo is public, which privacy-minded users value:
- F-Droid and IzzyOnDroid: app stores for open-source apps. Their users look for exactly this kind of app (no ads, no trackers). They need a license file in the repo, which it doesn't have yet.
- Put the GitHub link in the Play description. "Open source" builds trust for an app that runs in the background.

5. Keep the users you get
- Ask for ratings: Play's In-App Review API can show the rating prompt after, say, the tenth successful shake. Ratings strongly affect where you rank in search.
- Answer every review in Play Console, especially the bad ones. Most will be about shake sensitivity, and the calibration meter is your answer.