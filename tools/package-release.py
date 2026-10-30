"""Stage a versioned plugin and feature for the Eclipse p2 publisher."""
import datetime
import re
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent.parent
stamp = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=-5))).strftime('%Y%m%d%H%M%S')
manifest = (root / 'META-INF/MANIFEST.MF').read_text(encoding='utf-8')
base = re.search(r'Bundle-Version: ([^\r\n]+)', manifest).group(1)
version = base.replace('qualifier', stamp)
site = Path('D:/eclipse/releases/javafxcompanion') / version
(site / 'plugins').mkdir(parents=True)
(site / 'features').mkdir()
with zipfile.ZipFile(root / 'build/org.javafxcompanion-dev.jar') as source:
    assert 'org/javafxcompanion/ControllersView.class' in source.namelist()
    with zipfile.ZipFile(site / f'plugins/org.javafxcompanion_{version}.jar', 'w', zipfile.ZIP_DEFLATED) as target:
        for entry in source.infolist():
            data = source.read(entry.filename)
            if entry.filename == 'META-INF/MANIFEST.MF':
                data = re.sub(rb'Bundle-Version: [^\r\n]+', ('Bundle-Version: ' + version).encode(), data)
            target.writestr(entry.filename, data)
feature = (root / 'packaging/org.javafxcompanion.feature/feature.xml').read_text(encoding='utf-8')
feature = feature.replace(base, version).replace('version="0.0.0"', f'version="{version}"')
with zipfile.ZipFile(site / f'features/org.javafxcompanion.feature_{version}.jar', 'w', zipfile.ZIP_DEFLATED) as target:
    target.writestr('feature.xml', feature)
(site / 'site.xml').write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<site><feature url="features/org.javafxcompanion.feature_{version}.jar" id="org.javafxcompanion.feature" version="{version}"><category name="javafx"/></feature><category-def name="javafx" label="JavaFX Companion"/></site>
''', encoding='utf-8')
print(site)
