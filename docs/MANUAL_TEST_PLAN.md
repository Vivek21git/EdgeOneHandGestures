# Manual test plan: Motorola Edge 70 Fusion, Android 16

Nothing in this plan has been run yet. It is the checklist for the first real-device session. Record each
result as Pass / Fail / Partial plus a note; Diagnostics (in the app) shows why a gesture was ignored.

**Before starting:** complete Setup (restricted settings, enable service, battery Unrestricted). Turn on
"Show strips on screen" so you can see the zones. Use default mappings unless a test says otherwise.

## 0. Smoke test
| # | Step | Expected |
|---|------|----------|
| 0.1 | Dashboard status | "Active", left and right strips listening |
| 0.2 | Test area: swipe inward from each strip | "Swipe inward ... would run: Back" |
| 0.3 | Test area: start outside a strip | "Ignored: did not start inside an edge strip" |
| 0.4 | Real: swipe up / down / inward on the home screen | Lock / Recents / Back (see 1) |

## 1. Home screen (gesture navigation ON)
| Gesture | Expected | Notes to record |
|---|---|---|
| Inward from left, then right | Back (on launcher: nothing visible happens) | Does the system Back gesture ALSO fire / double-fire? |
| Up from either edge | Screen turns off and locks | Time to unlock; is fingerprint accepted? |
| Down from either edge | Recent apps opens | |
| Short diagonal up | Home | |
| Short diagonal down | Notification shade | |

Repeat the inward swipe at several heights inside and outside the strip's vertical range.
**Key question:** does Android's own Back gesture take the touch (ACTION_CANCEL shows in Diagnostics as
"Touch cancelled by the system") and what does the exclusion request change? Compare: strip width 8 dp vs 24 dp vs 40 dp.

## 2. Browser (Chrome)
- Open a long page; scroll normally in the middle, then scroll starting near the edge strip. Note how much of the edge becomes unusable.
- Inward swipe: goes back one page / closes tab per Chrome's behaviour.
- Swipe down: Recents works while a page is loading.

## 3. Messaging app
- Open a conversation; swipe inward from the edge: returns to the chat list.
- Edge swipes while the keyboard is open (see 5) and while a message is being typed: no text lost.
- Swipe-to-reply gestures on messages near the edge: note any conflict.

## 4. Full-screen app (video player, game, camera)
- Strips remain active in immersive mode? Record yes/no.
- Landscape video: edge swipes still recognised.
- Screenshot action on a DRM/secure screen: expect blank or refused, not a crash.

## 5. Keyboard open
- Open any text field, then use all gestures. The strip must not steal keyboard focus or close the keyboard on taps outside the strips.

## 6. Landscape orientation
- Rotate with a video or a rotating-capable app. Strips must re-appear flush with the new left/right edges within about a second, with correct height proportion.
- Rotate several times quickly: no duplicate strips (Diagnostics shows exactly one per edge).
- If the phone has a camera cut-out: strips still reach the physical edge.

## 7. Screen locking
- Up swipe locks. Wake the phone: strips are active again after unlock.
- Lock, then try gestures on the lock screen: record what happens (strips normally should not act on the keyguard).
- Confirm the phone locks rather than powers off, and note unlock-method behaviour.

## 8. Recent apps
- Down swipe opens Recents; a second down swipe or Back closes it.
- Works from inside a full-screen app and from the home screen.

## 9. Android Back and predictive Back
- In an app that supports predictive Back (Settings, recent Chrome), perform an inward swipe from the strip: is the predictive animation shown, or only an instant Back?
- Do a *slow* inward drag: does the system Back gesture win (animation) instead of this app? Record the behaviour; this is expected to differ from Samsung's.
- Switch system navigation to 3-button mode and repeat 1: the conflict should disappear.

## 10. Service restart / resilience
- Dashboard master switch off: strips vanish within a second; on: they return.
- Force-stop the app (App info): Accessibility service should restart on its own; check strips return and settings are intact.
- Reboot the phone: service starts, strips appear without opening the app.
- Leave the phone idle for 1 hour on battery: strips still work (checks Motorola background limits).

## 11. Permission revocation
- Turn the accessibility service off in Settings: strips disappear immediately; dashboard shows "Accessibility service is off".
- Turn it back on: strips return, settings unchanged.
- Revoke via "Restricted settings" state (reinstall the APK): follow Setup step 1 again.

## 12. Settings behaviour
- Change width, range, sensitivity, distance on one edge; the other edge is unaffected; the change applies live.
- Remap a gesture to Launch app, to Screenshot, to Quick settings, to Split screen; disable one gesture; set one to No action.
- Pick a Launch-shortcut target (only apps that offer one will appear). Note failures shown in Diagnostics.
- Kill and reopen the app: all settings persisted.
- "Reset all settings to defaults" restores the original mappings.

## 13. Accidental activation
- Hold the phone one-handed and scroll normally for 5 minutes with default settings; count false triggers (up = lock is the costly one).
- If there are false triggers: raise minimum distance, lower sensitivity, or shrink the vertical range.
