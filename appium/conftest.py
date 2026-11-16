import os
import subprocess
from pathlib import Path

import pytest
from appium import webdriver
from appium.options.android import UiAutomator2Options
from appium.webdriver.common.appiumby import AppiumBy
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

import fake_server as fake_server_module

PKG = "dev.dublinbustracker.app"
HERE = Path(__file__).parent
ARTIFACTS = HERE / "artifacts"
DEFAULT_APK = HERE.parent / "app/build/outputs/apk/debug/app-debug.apk"


def by_tag(driver, tag, timeout=15):
    # testTagsAsResourceId gives a bare resource-id (no "pkg:id/"); By.ID would prepend the package, so match it exactly via UiSelector.
    locator = (AppiumBy.ANDROID_UIAUTOMATOR, f'new UiSelector().resourceId("{tag}")')
    return WebDriverWait(driver, timeout).until(
        EC.presence_of_element_located(locator), f"no element tagged {tag}"
    )


def _boot_completed(_):
    out = subprocess.run(["adb", "shell", "getprop", "sys.boot_completed"], capture_output=True, text=True, timeout=10)
    return out.stdout.strip() == "1"


def _dismiss_anr(driver):
    # Right after emulator boot a "System UI isn't responding" dialog can cover the app.
    for el in driver.find_elements(AppiumBy.ID, "android:id/aerr_wait"):
        el.click()


@pytest.fixture(scope="session")
def fake_server():
    try:
        server = fake_server_module.start(8080, "0.0.0.0")
    except OSError:
        pytest.exit("port 8080 busy")
    yield server
    server.shutdown()
    server.server_close()


@pytest.fixture(scope="session")
def driver(fake_server):
    WebDriverWait(None, 120, poll_frequency=2).until(_boot_completed, "emulator not booted")
    options = UiAutomator2Options()
    options.platform_name = "Android"
    options.app = os.environ.get("APK_PATH", str(DEFAULT_APK))
    options.no_reset = True
    drv = webdriver.Remote("http://127.0.0.1:4723", options=options)
    try:
        yield drv
    finally:
        drv.quit()


@pytest.fixture
def app(driver):
    _dismiss_anr(driver)
    driver.execute_script("mobile: clearApp", {"appId": PKG})
    driver.activate_app(PKG)
    return driver


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    report = (yield).get_result()
    if report.when == "call" and report.failed:
        try:
            drv = item.funcargs.get("driver") or item.funcargs.get("app")
            ARTIFACTS.mkdir(exist_ok=True)
            drv.save_screenshot(str(ARTIFACTS / f"{item.name}.png"))
        except Exception:
            pass  # a dead Appium session must not mask the real test failure with an INTERNALERROR
