"""Turns the 1.2.4 (Honor) binary manifest into the Breezy edition's.

usage: breezy_manifest.py <in AndroidManifest.xml> <out AndroidManifest.xml> [--debug]

--debug keeps FlipClockTicker exported, for the adb test hooks (ClockLog INFLATE_TEST / CASCADE_TEST).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from axml import Axml  # noqa: E402

DROP_PERMISSIONS = [
    'android.permission.INTERNET',
    'android.permission.ACCESS_COARSE_LOCATION',
    'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.ACCESS_BACKGROUND_LOCATION',
    # Only the unused ClockTickService (dropped below) needed these; nothing takes wake locks.
    # (POST_NOTIFICATIONS stays: WeatherNotification.)
    'android.permission.FOREGROUND_SERVICE',
    'android.permission.FOREGROUND_SERVICE_SPECIAL_USE',
    'android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',
    'android.permission.WAKE_LOCK',
]
OPENMETEO = 'com.htc.widget.weatherclock.openmeteo.'

src, dst = sys.argv[1:3]
DEBUG = '--debug' in sys.argv[3:]
a = Axml(open(src, 'rb').read())
strings = a.pool.strings


def rename(old, new):
    assert strings.count(old) == 1, old
    strings[strings.index(old)] = new


for perm in DROP_PERMISSIONS:
    for i in a.find('uses-permission', name=perm):
        a.remove(i)
for i in a.find('receiver', name=OPENMETEO + 'WeatherMotion$Receiver'):
    a.remove(i)
for i in a.find('service', name=OPENMETEO + 'ClockTickService'):
    a.remove(i)

TYPE_INT_BOOLEAN = 0x12


def set_bool(i, attr_name, value):
    """Sets an existing android: boolean attribute of the element at node i."""
    for at in a.nodes[i].attrs:
        if a.s(at[1]) == attr_name:
            at[2], at[5], at[6] = 0xFFFFFFFF, TYPE_INT_BOOLEAN, 0xFFFFFFFF if value else 0
            return
    raise KeyError(attr_name)


# Nothing outside the app needs these: the ticker gets its own alarms and system broadcasts (which reach
# non-exported receivers), and the two activities are the earlier modder's adb test screens.
for tag, name in [('receiver', 'FlipClockTicker'), ('activity', 'FlipStyleActivity'), ('activity', 'ClockPulseActivity')]:
    if DEBUG and name == 'FlipClockTicker':
        continue
    for i in a.find(tag, name=OPENMETEO + name):
        set_bool(i, 'exported', False)
# Backups would carry the saved coordinates off the phone.
set_bool(a.find('application')[0], 'allowBackup', False)

# Package visibility for Breezy (the Honor edition's entry), and the version name.
rename('com.hihonor.android.totemweather', 'org.breezyweather')
rename('10.10.1059527-android36-v1.2.4', '10.10.1059527-android36-v1.2.9-breezy')

perm = a.element('uses-permission', [('name', 'org.breezyweather.READ_PROVIDER')])
last = a.find('uses-permission')[-1]
a.nodes[a.end_of(last) + 1:a.end_of(last) + 1] = perm

# Exact flip-clock minute ticks without the user granting "Alarms & reminders": USE_EXACT_ALARM (API 33+) is
# granted at install. Without exact alarms Android may deliver the minute tick up to ~45 s late.
perm = a.element('uses-permission', [('name', 'android.permission.USE_EXACT_ALARM')])
last = a.find('uses-permission')[-1]
a.nodes[a.end_of(last) + 1:a.end_of(last) + 1] = perm

receiver = a.element('receiver', [('name', OPENMETEO + 'BreezyUpdateReceiver'), ('exported', True)], [
    a.element('intent-filter', [], [a.element('action', [('name', 'org.breezyweather.ACTION_UPDATE_NOTIFIER')])])])
a.insert_before_end(a.find('application')[0], receiver)

# Watches for the lock screen going away after SCREEN_ON, for the Sense unlock flip (USER_PRESENT arrives late).
watch = a.element('service', [('name', OPENMETEO + 'UnlockWatchJob'), ('permission', 'android.permission.BIND_JOB_SERVICE'),
                              ('exported', False)])
a.insert_before_end(a.find('application')[0], watch)

# Screen-on notice by a non-wakeup alarm, delivered at wake even to a frozen app (ScreenWatch).
watch = a.element('receiver', [('name', OPENMETEO + 'ScreenWatch'), ('exported', False)])
a.insert_before_end(a.find('application')[0], watch)

# The weather animation after unlocking (UnlockWeather): an overlay window, shown only over the home screen,
# which usage access tells apart (both granted by the user in the settings). Seeing the launchers needs a query.
for name in ['android.permission.SYSTEM_ALERT_WINDOW', 'android.permission.PACKAGE_USAGE_STATS']:
    perm = a.element('uses-permission', [('name', name)])
    last = a.find('uses-permission')[-1]
    a.nodes[a.end_of(last) + 1:a.end_of(last) + 1] = perm
home = a.element('intent', [], [a.element('action', [('name', 'android.intent.action.MAIN')]),
                                a.element('category', [('name', 'android.intent.category.HOME')])])
a.insert_before_end(a.find('queries')[0], home)

# Alarms and jobs again right after an update (UpdateReceiver), and the unlock & battery check screen.
upd = a.element('receiver', [('name', OPENMETEO + 'UpdateReceiver'), ('exported', False)], [
    a.element('intent-filter', [], [a.element('action', [('name', 'android.intent.action.MY_PACKAGE_REPLACED')])])])
a.insert_before_end(a.find('application')[0], upd)
diag = a.element('activity', [('name', OPENMETEO + 'DiagnosticsActivity'), ('exported', False)])
a.insert_before_end(a.find('application')[0], diag)

# The weather widgets' settings screen (HtcClockSetting hands weather widgets over to it).
settings = a.element('activity', [('name', OPENMETEO + 'WeatherSettingsActivity'), ('exported', False)])
a.insert_before_end(a.find('application')[0], settings)

open(dst, 'wb').write(a.build())
print('permissions:', [a.attr(a.nodes[i], 'name') for i in a.find('uses-permission')])
print('activities:', [a.attr(a.nodes[i], 'name') for i in a.find('activity')][-3:])
print('receivers:', [a.attr(a.nodes[i], 'name') for i in a.find('receiver')])
print('queries:', [a.attr(a.nodes[i], 'name') for i in a.find('package')])
