"""Fetch the exact upstream sources used by the embedded engine."""
import pathlib
import subprocess

REVISION = '46ca246de9bb1c35269722a6240d37d9dfd79cad'
root = pathlib.Path(__file__).resolve().parent.parent
destination = root / 'vendor' / 'llama.cpp'
def git(*args):
    subprocess.run(['git', '-C', str(destination), *args], check=True)
if not (destination / '.git').exists():
    destination.mkdir(parents=True, exist_ok=True)
    subprocess.run(['git', 'init', str(destination)], check=True)
    git('config', 'core.longpaths', 'true')
    git('remote', 'add', 'origin', 'https://github.com/ggml-org/llama.cpp.git')
    git('fetch', '--depth', '1', 'origin', REVISION)
    git('sparse-checkout', 'init', '--cone')
    git('sparse-checkout', 'set', 'cmake', 'common', 'ggml', 'include', 'src', 'tools/server', 'tools/mtmd', 'tools/ui', 'vendor', 'scripts')
    git('checkout', '--detach', 'FETCH_HEAD')
actual = subprocess.check_output(['git', '-C', str(destination), 'rev-parse', 'HEAD'], text=True).strip()
if actual != REVISION:
    raise SystemExit('Unexpected llama.cpp revision: ' + actual)
print('llama.cpp source ready: ' + actual)
