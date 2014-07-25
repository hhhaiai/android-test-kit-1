Espresso
========
Android's Espresso testing library, rebuilt for use with Gradle. Like [Double Espresso](https://github.com/JakeWharton/double-espresso), but without the restructuring.

Changes
-------
See the [full diff](https://github.com/DocuSignDev/android-test-kit/compare/1e71a17...master?w=1) for additions below.

- Gradle-fied
- Matching across multiple root views
- Greatly improved `isDisplayingAtLest(int percentage)`
- Added `withHintText` matcher
- Screenshots

Screenshots
-----------
Espresso will now automatically take screenshots during `perform(ViewInteraction vi)` and `check(ViewAssertion va)` to help debug any failures. When a test does fail, Espresso will automatically dump the final failure screenshot and the screens leading up to it from the current test method to your app's data directory with these naming schemes:

```
files/test-results/{class}-{method}/failure.jpg // final failure 
files/test-results/{class}-{method}/snapshot-{time}.jpg // screens leading up to failure
```
