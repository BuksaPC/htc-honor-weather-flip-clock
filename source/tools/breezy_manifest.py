"""Turns the 1.2.4 (Honor) binary manifest into the Breezy edition's.

usage: breezy_manifest.py <in AndroidManifest.xml> <out AndroidManifest.xml>
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
]
OPENMETEO = 'com.htc.widget.weatherclock.openmeteo.'

src, dst = sys.argv[1:3]
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

# Package visibility for Breezy (the Honor edition's entry), and the version name.
rename('com.hihonor.android.totemweather', 'org.breezyweather')
rename('10.10.1059527-android36-v1.2.4', '10.10.1059527-android36-v1.2.5-breezy')

perm = a.element('uses-permission', [('name', 'org.breezyweather.READ_PROVIDER')])
last = a.find('uses-permission')[-1]
a.nodes[a.end_of(last) + 1:a.end_of(last) + 1] = perm

receiver = a.element('receiver', [('name', OPENMETEO + 'BreezyUpdateReceiver'), ('exported', True)], [
    a.element('intent-filter', [], [a.element('action', [('name', 'org.breezyweather.ACTION_UPDATE_NOTIFIER')])])])
a.insert_before_end(a.find('application')[0], receiver)

# The weather widgets' settings screen (HtcClockSetting hands weather widgets over to it).
settings = a.element('activity', [('name', OPENMETEO + 'WeatherSettingsActivity'), ('exported', False)])
a.insert_before_end(a.find('application')[0], settings)

open(dst, 'wb').write(a.build())
print('permissions:', [a.attr(a.nodes[i], 'name') for i in a.find('uses-permission')])
print('activities:', [a.attr(a.nodes[i], 'name') for i in a.find('activity')][-3:])
print('receivers:', [a.attr(a.nodes[i], 'name') for i in a.find('receiver')])
print('queries:', [a.attr(a.nodes[i], 'name') for i in a.find('package')])
