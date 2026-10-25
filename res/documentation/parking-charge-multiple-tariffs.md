# SCEE parking charge: multiple tariffs and custom intervals

This branch extends the existing `AddParkingCharge` quest in SCEE. It does not
register another quest and is not intended for StreetComplete upstream.

Base: `Helium314/SCEE:modified` at
`bb01e6c49ed5ee95ec442bb88758f7a4bcd2ca53`.

## Implemented

- Keep the compact single-price form by default.
- Other answer -> Multiple tariffs: retain the existing row, add a second row,
  and expose add/remove controls. Every row has its own price and billing interval.
- Other answer -> Other billing interval: enable a positive integer duration
  (1..9999) for a single rate. Units are minutes, hours, and days.
- Write all independent ticket alternatives to `charge`, separated by `;`,
  and keep updating `check_date:charge`. Do not convert prices to hourly rates.
- Load supported existing tariffs on resurvey. Block unsupported or lossy values
  rather than silently replacing them with a partial result.
- Require complete rows and enforce the 255-character tag-value limit.
- Preserve stable row identifiers, selected units, and incomplete editor state.
- Include English/German labels and model/editor/serialization tests.

Example:

```text
charge=0.50 EUR/15 minutes;2 EUR/4 hours;5 EUR/day
```

These are independently available tickets, not successive billing steps.
Calendar conditions, weekday-specific prices, daily caps, and `charge:conditional`
editing are outside this initial implementation. The quest filter and registration
are unchanged.

## Validation and remaining work

42 isolated Kotlin/JVM tests for `Charge.kt` and `ChargeEditorState.kt` passed
again when this branch was prepared. They use the pinned SCEE duration and money
formatting helpers and real `kotlin.test` assertions. The isolated runner replaces
only the serialization annotation with a marker; this is **not** a full build.

The following are still required before submitting a ready-for-review PR:

- Run `./gradlew :app:testAndroidHostTest` with the project toolchain, including
  `ChargeEditorStateSerializationTest` (two tests not run in the isolated runner).
- Compile the Android/Compose application; no APK has been built here.
- Test narrow screens, large fonts, keyboard visibility, German/English locales,
  row deletion, rotation/recreation with blank fields, and resurvey behavior.
- Check changeset output and preservation of unrelated tags.
- Integrate the two small `strings_parking_charge_ee.xml` files into the existing
  Weblate-managed `strings_ee.xml` catalogs before upstream submission, removing
  the small files at the same time to avoid duplicate resource keys. They are
  separate Compose resource files in this initial branch; existing catalogs are
  left untouched.

The implementation was prepared with AI assistance and needs human review.
No pull request is opened by creating this branch.
