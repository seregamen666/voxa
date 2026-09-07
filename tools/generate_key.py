"""
Генератор лицензионных ключей для Voxa.

Запуск:
    python generate_key.py            -> один новый ключ
    python generate_key.py 5          -> пять новых ключей

Ключ проверяется в приложении полностью офлайн (без сервера) — секрет
зашит и здесь, и в app/src/main/java/com/voxa/dictation/License.kt.
Если когда-нибудь захотите сменить секрет — поменяйте его в ОБОИХ местах,
иначе старые/новые ключи перестанут совпадать.
"""
import hashlib
import hmac
import secrets
import sys

SECRET_HEX = "4248cebc92d01a1c8ebc126c265a47e06bd42adb7af923898b9948161b32740e"
SECRET = bytes.fromhex(SECRET_HEX)


def generate_key() -> str:
    serial = secrets.token_hex(4).upper()  # 8 hex-символов
    sig = hmac.new(SECRET, serial.encode(), hashlib.sha256).hexdigest()[:6].upper()
    return f"VOXA-{serial}-{sig}"


def verify_key(key: str) -> bool:
    parts = key.strip().upper().replace(" ", "").split("-")
    if len(parts) != 3 or parts[0] != "VOXA":
        return False
    serial, sig = parts[1], parts[2]
    expected = hmac.new(SECRET, serial.encode(), hashlib.sha256).hexdigest()[:6].upper()
    return hmac.compare_digest(sig, expected)


if __name__ == "__main__":
    count = int(sys.argv[1]) if len(sys.argv) > 1 else 1
    for _ in range(count):
        key = generate_key()
        assert verify_key(key)
        print(key)
