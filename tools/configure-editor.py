"""Update the PDE manifest with local Scene Builder/JavaFX dependencies."""
from pathlib import Path
root = Path(__file__).resolve().parent.parent
path = root / 'META-INF/MANIFEST.MF'
text = path.read_text(encoding='utf-8')
text = text.split('Bundle-ClassPath:')[0].rstrip() + '\n'
jars = sorted('lib/' + p.name for p in (root/'lib').glob('*.jar'))
text += 'Bundle-ClassPath: .,\n ' + ',\n '.join(jars) + '\n'
dlls = sorted('native/' + p.name for p in (root/'native').glob('*.dll'))
text += 'Bundle-NativeCode: ' + ';\n '.join(dlls) + ';\n osname=win32;processor=x86-64\n\n'
path.write_text(text, encoding='utf-8')
