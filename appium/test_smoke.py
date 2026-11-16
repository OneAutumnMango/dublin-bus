from appium.webdriver.common.appiumby import AppiumBy
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

from conftest import by_tag


def _desc(star):
    # The tag lands on the IconButton; the content-desc is on its child icon node.
    return star.find_element(AppiumBy.XPATH, ".//*[@content-desc]").get_attribute("content-desc")


def _open_connell(app):
    by_tag(app, "search_field").send_keys("connell")
    by_tag(app, "stop_row_stop-1").click()


def test_search_stop_shows_arrivals(fake_server, app):
    _open_connell(app)
    # The tag is on the row container; its text lives in child TextViews.
    by_tag(app, "arrival_row_trip-a").find_element(AppiumBy.XPATH, './/*[contains(@text, "Ballinteer")]')
    by_tag(app, "arrival_row_trip-b").find_element(AppiumBy.XPATH, './/*[contains(@text, "Dun Laoghaire")]')


def test_toggle_favourite_appears_in_favourites(fake_server, app):
    _open_connell(app)
    by_tag(app, "arrival_row_trip-a")  # detail screen is showing, so this star is the header's, not a search row's
    star = by_tag(app, "favourite_star")
    assert _desc(star) == "Add to favourites"
    star.click()
    WebDriverWait(app, 15).until(
        lambda d: _desc(by_tag(d, "favourite_star", 2)) == "Remove from favourites"
    )
    app.back()
    by_tag(app, "nav_favourites").click()
    WebDriverWait(app, 15).until(
        EC.presence_of_element_located((AppiumBy.ANDROID_UIAUTOMATOR, 'new UiSelector().textContains("O\'Connell Street")'))
    )


def test_settings_save_and_test_reports_success(fake_server, app):
    by_tag(app, "nav_settings").click()
    field = by_tag(app, "settings_server_address")
    field.clear()
    field.send_keys("http://10.0.2.2:8080/")
    # The button sits low in a scrolling column; UiAutomator only lists on-screen nodes.
    app.find_element(
        AppiumBy.ANDROID_UIAUTOMATOR,
        'new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().resourceId("settings_save_test"))',
    )
    by_tag(app, "settings_save_test").click()
    # status.json has no "routes" key, so routeCount is 0.
    WebDriverWait(app, 15).until(
        EC.presence_of_element_located((AppiumBy.ANDROID_UIAUTOMATOR, 'new UiSelector().text("Connected. 2 stops, 0 routes, feeds: fake")'))
    )
