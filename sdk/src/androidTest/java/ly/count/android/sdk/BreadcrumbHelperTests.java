package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BreadcrumbHelperTests {

    @Test
    public void addBreadcrumb() {
        BreadcrumbHelper breadcrumbHelper = new BreadcrumbHelper(5, new ModuleLog());
        breadcrumbHelper.addBreadcrumb("test", 10);
        Assert.assertEquals("test\n", breadcrumbHelper.getBreadcrumbs());
    }

    @Test
    public void addBreadcrumb_emptyString() {
        BreadcrumbHelper breadcrumbHelper = new BreadcrumbHelper(5, new ModuleLog());
        breadcrumbHelper.addBreadcrumb("Test", 10);
        breadcrumbHelper.addBreadcrumb("", 10);
        Assert.assertEquals("Test\n", breadcrumbHelper.getBreadcrumbs());
    }

    @Test
    public void addBreadcrumb_null() {
        BreadcrumbHelper breadcrumbHelper = new BreadcrumbHelper(5, new ModuleLog());
        breadcrumbHelper.addBreadcrumb("Test", 10);
        breadcrumbHelper.addBreadcrumb(null, 10);
        Assert.assertEquals("Test\n", breadcrumbHelper.getBreadcrumbs());
    }

    @Test
    public void addBreadcrumb_exceedsCharacterLimit() {
        BreadcrumbHelper breadcrumbHelper = new BreadcrumbHelper(5, new ModuleLog());
        breadcrumbHelper.addBreadcrumb("Test", 2);
        Assert.assertEquals("Te\n", breadcrumbHelper.getBreadcrumbs());
    }

    @Test
    public void addBreadcrumb_exceedsLimit() {
        BreadcrumbHelper breadcrumbHelper = new BreadcrumbHelper(2, new ModuleLog());
        breadcrumbHelper.addBreadcrumb("Test", 3);
        Assert.assertEquals("Tes\n", breadcrumbHelper.getBreadcrumbs());
        breadcrumbHelper.addBreadcrumb("Doggy", 3);
        Assert.assertEquals("Tes\nDog\n", breadcrumbHelper.getBreadcrumbs());
        breadcrumbHelper.addBreadcrumb("Geralt", 3);
        Assert.assertEquals("Dog\nGer\n", breadcrumbHelper.getBreadcrumbs());
    }

    @Test
    public void clearBreadcrumbs() {
        BreadcrumbHelper breadcrumbHelper = new BreadcrumbHelper(2, new ModuleLog());
        breadcrumbHelper.addBreadcrumb("Test", 3);
        Assert.assertEquals("Tes\n", breadcrumbHelper.getBreadcrumbs());
        breadcrumbHelper.clearBreadcrumbs();
        Assert.assertEquals("", breadcrumbHelper.getBreadcrumbs());
    }
}
