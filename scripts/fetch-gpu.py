"""Fetch pinned header-only dependencies for the Android Vulkan backend."""
import pathlib, subprocess
root = pathlib.Path(__file__).resolve().parent.parent
for name, repository, revision in [
    ('vulkan-headers', 'KhronosGroup/Vulkan-Headers', '6802bb4733b63ed5efd3adb308a6c885ef180ea1'),
    ('spirv-headers', 'KhronosGroup/SPIRV-Headers', '496543121ce6419f23d6fa5d7194ba66c36212d2'),
]:
    destination = root / 'vendor' / name
    def git(*args):
        return subprocess.check_output(['git', '-C', str(destination), *args], text=True)
    if not (destination / '.git').exists():
        destination.mkdir(parents=True, exist_ok=True)
        subprocess.run(['git', 'init', str(destination)], check=True)
        git('config', 'core.longpaths', 'true')
        git('remote', 'add', 'origin', 'https://github.com/' + repository + '.git')
    try:
        actual = git('rev-parse', 'HEAD').strip()
    except subprocess.CalledProcessError:
        actual = None
    if actual is None:
        git('fetch', '--depth', '1', 'origin', revision)
        git('checkout', '--detach', 'FETCH_HEAD')
    if git('rev-parse', 'HEAD').strip() != revision:
        raise SystemExit('Unexpected dependency revision: ' + name)
    print(name + ' ready: ' + revision)
