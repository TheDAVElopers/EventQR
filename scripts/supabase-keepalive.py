#!/usr/bin/env python3
"""Ping Supabase REST API to prevent free-tier project pause from inactivity."""

import os
import sys
import urllib.error
import urllib.request

PROJECT_REF = os.environ.get("SUPABASE_PROJECT_REF", "hmjrqfwtpinguhwgkgpw")
ANON_KEY = os.environ.get("SUPABASE_ANON_KEY") or (
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhtanJxZnd0cGluZ3Vod2drZ3B3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk2ODUxMjcsImV4cCI6MjA5NTI2MTEyN30.IxQgUnLCHtdW6SlbFdxzRWJy7RK0n1RHNXIC2bkEXig"
)
TABLE_NAME = os.environ.get("SUPABASE_TABLE_NAME", "user_profiles")

URL = f"https://{PROJECT_REF}.supabase.co/rest/v1/{TABLE_NAME}?limit=1"

HEADERS = {
    "apikey": ANON_KEY,
    "Authorization": f"Bearer {ANON_KEY}",
}


def ping() -> bool:
    request = urllib.request.Request(URL, headers=HEADERS, method="GET")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            print(f"OK: {response.status} {URL}")
            return True
    except urllib.error.HTTPError as exc:
        print(f"FAIL: HTTP {exc.code} {URL}")
        return False
    except urllib.error.URLError as exc:
        print(f"FAIL: {exc.reason} {URL}")
        return False


if __name__ == "__main__":
    sys.exit(0 if ping() else 1)
