from pathlib import Path
import json
import sqlite3
import struct
import sys
import tarfile
import time
import xml.etree.ElementTree as ET

root = Path(sys.argv[2]).resolve()
root.mkdir(parents=True, exist_ok=True)
database_name = 'databases/streetcomplete_v2.db'
preferences_name = 'shared_prefs/de.westnordost.streetcomplete.expert.debug_preferences.xml'
with tarfile.open(sys.argv[1]) as archive:
    for member in archive.getmembers():
        if member.isfile() and member.name in (database_name, database_name + '-wal',
                                               database_name + '-shm', preferences_name):
            target = root / member.name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.extractfile(member).read())

database = root / database_name
con = sqlite3.connect(database)
assert con.execute('pragma user_version').fetchone()[0] == 22
for table in ('osm_element_edits', 'osm_note_edits'):
    assert con.execute(f'select count(*) from {table}').fetchone()[0] == 0
node_id = 9000000000640
now = int(time.time() * 1000)
tags = {'fixme': 'SCEE isolated runtime fixture; never upload', 'name': 'SCEE CI fixture'}
con.execute('insert or replace into osm_nodes (id,version,latitude,longitude,tags,timestamp,last_sync) '
            'values (?,?,?,?,?,?,?)', (node_id, 1, 52.52025, 13.405, json.dumps(tags), now, now))
con.execute('delete from osm_quests')
con.execute('insert into osm_quests values (?,?,?,?,?)', ('ShowFixme', node_id, 'NODE', 52.52025, 13.405))
con.execute('insert or ignore into quest_presets values (?,?)', (7, 'Legacy-SCEE-63.4'))
con.execute('insert or replace into quest_visibility values (?,?,?)', (7, 'ShowFixme', 1))
con.commit()
con.execute('pragma wal_checkpoint(truncate)')
assert con.execute('pragma integrity_check').fetchone()[0] == 'ok'
con.close()

tree = ET.parse(root / preferences_name)
preferences = tree.getroot()
assert not any('token' in entry.get('name', '').lower() and (entry.text or '').strip()
               for entry in preferences)
def put(kind, key, value):
    for entry in list(preferences):
        if entry.get('name') == key:
            preferences.remove(entry)
    ET.SubElement(preferences, kind, name=key, value=str(value).lower())

for key in ('hasShownTutorial', 'hasShownOverlaysTutorial', 'expert_mode', 'display.zoomButtons',
            'quest_settings_per_preset'):
    put('boolean', key, True)
put('long', 'selectedQuestsPreset', 7)
for key, value in {'map.latitude': 52.52025, 'map.longitude': 13.405, 'map.rotation2': 0.0,
                   'map.tilt2': 0.0, 'map.zoom2': 18.0}.items():
    put('long', key, struct.unpack('>q', struct.pack('>d', value))[0])
for entry in list(preferences):
    if entry.get('name') == 'selectedOverlay':
        preferences.remove(entry)
tree.write(root / preferences_name, encoding='utf-8', xml_declaration=True)
with tarfile.open(root / 'fixture.tar', 'w') as archive:
    for name in (database_name, preferences_name):
        archive.add(root / name, arcname=name)
print(json.dumps({'fixture': str(root / 'fixture.tar'), 'node_id': node_id,
                  'quest': 'ShowFixme', 'queued_osm_writes': 0}))

