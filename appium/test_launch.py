from conftest import by_tag


def test_app_launches_on_search_tab(app):
    assert by_tag(app, "nav_search").is_displayed()
    assert by_tag(app, "search_field").is_displayed()
