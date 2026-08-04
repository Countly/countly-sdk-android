[![Codacy Badge](https://app.codacy.com/project/badge/Grade/b26d1acc435c47af88b4e4b9eb94f59f)](https://app.codacy.com/gh/Countly/countly-sdk-android/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)

# Countly Android SDK

This repository contains the Countly Android SDK, which can be integrated into Android applications. The Countly Android SDK is intended to be used with [Countly Lite](https://countly.com/lite), [Countly Flex](https://countly.com/flex), [Countly Enterprise](https://countly.com/enterprise).

## What is Countly?
[Countly](https://count.ly) is a product analytics solution and innovation enabler that helps teams track product performance and customer journey and behavior across [mobile](https://count.ly/mobile-analytics), [web](https://count.ly/web-analytics),
and [desktop](https://count.ly/desktop-analytics) applications. [Ensuring privacy by design](https://count.ly/privacy-by-design), Countly allows you to innovate and enhance your products to provide personalized and customized customer experiences, and meet key business and revenue goals.

Track, measure, and take action - all without leaving Countly.

* **Questions or feature requests?** [Join the Countly Community on Discord](https://discord.gg/countly)
* **Looking for the Countly Server?** [Countly Server repository](https://github.com/Countly/countly-server)
* **Looking for other Countly SDKs?** [An overview of all Countly SDKs for mobile, web and desktop](https://support.count.ly/hc/en-us/articles/360037236571-Downloading-and-Installing-SDKs#h_01H9QCP8G5Y9PZJGERZ4XWYDY9)

## Integrating Countly SDK in your projects

For a detailed description on how to use this SDK [check out our documentation](https://support.count.ly/hc/en-us/articles/360037754031-Android).

For information about how to add the SDK to your project, please check [this section of the documentation](https://support.count.ly/hc/en-us/articles/360037754031-Android#h_01HAVQDM5SZRDX323EDDTNNMEF).

You can find minimal SDK integration information for your project in [this section of the documentation](https://support.count.ly/hc/en-us/articles/360037754031-Android#h_01HAVQDM5SPR8WSAQ76DEREE2E).

For an example integration of this SDK, you can have a look [here](https://github.com/Countly/countly-sdk-android/tree/master/app).

This SDK supports the following features:
* [Analytics](https://support.count.ly/hc/en-us/articles/4431589003545-Analytics)
* [Push Notifications](https://support.count.ly/hc/en-us/articles/4405405459225-Push-Notifications)
* [User Profiles](https://support.count.ly/hc/en-us/articles/4403281285913-User-Profiles)
* [Crash Reports](https://support.count.ly/hc/en-us/articles/4404213566105-Crashes-Errors)
* [A/B Testing](https://support.count.ly/hc/en-us/articles/4416496362393-A-B-Testing-)
* [Performance Monitoring](https://support.count.ly/hc/en-us/articles/4734457847705-Performance)
* [Feedback Widgets](https://support.count.ly/hc/en-us/articles/4652903481753-Feedback-Surveys-NPS-and-Ratings-)

## Security
Security is very important to us. If you discover any issue regarding security, please disclose the information responsibly by sending an email to security@count.ly and **not by creating a GitHub issue**.

### The `sdk-nw` artifact
Alongside `ly.count.android:sdk`, the SDK is published as `ly.count.android:sdk-nw`, where "nw" stands for "no web view".
It exists for organizations that need a hardened baseline to hold across a large number of applications, where relying on every application to apply the same configuration is not practical.
Two settings are enforced by the artifact itself:

* All WebView based UI is disabled, so no WebView is created or shown for the Content feature, Feedback Widgets, or the rating popup.
* The SDK's console logging is kept off in production (non-debuggable) builds, even when logging is enabled in the configuration.

Both settings are applied during `init` and can not be turned off, so no application, wrapper, or configuration mistake can weaken them.
Everything else is identical to `ly.count.android:sdk`: the same source, the same package names, and the same API.
Switching is a one line dependency change and requires no changes to your imports or code.

```gradle
implementation 'ly.count.android:sdk-nw:26.1.5'
```

Version numbers match the regular release they were built from, so `ly.count.android:sdk-nw:26.1.5` contains the same SDK as `ly.count.android:sdk:26.1.5`.
Depend on either `ly.count.android:sdk` or `ly.count.android:sdk-nw`, never both, because they contain the same classes.
Applications that need the WebView based UI, such as the Content feature or Feedback Widgets, should use `ly.count.android:sdk`.

## Badges
If you like Countly, [why not use one of our badges](https://count.ly/brand-assets) and give a link back to us so others know about this wonderful platform?

<a href="https://count.ly/f/badge" rel="nofollow"><img style="width:145px;height:60px" src="https://count.ly/badges/dark.svg?v2" alt="Countly - Product Analytics" /></a>

```JS
<a href="https://count.ly/f/badge" rel="nofollow"><img style="width:145px;height:60px" src="https://count.ly/badges/dark.svg" alt="Countly - Product Analytics" /></a>
```

<a href="https://count.ly/f/badge" rel="nofollow"><img style="width:145px;height:60px" src="https://count.ly/badges/light.svg?v2" alt="Countly - Product Analytics" /></a>

```JS
<a href="https://count.ly/f/badge" rel="nofollow"><img style="width:145px;height:60px" src="https://count.ly/badges/light.svg" alt="Countly - Product Analytics" /></a>
```

## How can I help you with your efforts?
Glad you asked! For community support, feature requests, and engaging with the Countly Community, please join us at [our Discord Server](https://discord.gg/countly). We're excited to have you there!

Also, we are on [Twitter](https://twitter.com/gocountly) and [LinkedIn](https://www.linkedin.com/company/countly) if you would like to keep up with Countly related updates.
