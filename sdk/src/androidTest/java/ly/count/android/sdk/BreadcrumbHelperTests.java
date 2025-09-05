package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BreadcrumbHelperTests {

    /**
     * "addBreadcrumb"
     * should add a breadcrumb to the breadcrumb list.
     */
    @Test
    public void addBreadcrumb() {
        BreadcrumbHelper breadcrumbHelper = new BreadcrumbHelper(5, new ModuleLog());
        breadcrumbHelper.addBreadcrumb("test", 10);
        Assert.assertEquals(list("test"), breadcrumbHelper.getBreadcrumbs());
    }

    /**
     * "addBreadcrumb" with empty string
     * should not add an empty string to the breadcrumb list.
     */
    @Test(expected = AssertionError.class)
    public void addBreadcrumb_emptyString() {
        BreadcrumbHelper breadcrumbHelper = new BreadcrumbHelper(5, new ModuleLog());
        breadcrumbHelper.addBreadcrumb("", 10, 5);
    }

    /**
     * "addBreadcrumb" with null
     * should not add a null string to the breadcrumb list.
     */
    @Test(expected = AssertionError.class)
    public void addBreadcrumb_null() {
        BreadcrumbHelper breadcrumbHelper = new BreadcrumbHelper(5, new ModuleLog());
        breadcrumbHelper.addBreadcrumb(null, 10, 5);
    }

    /**
     * "addBreadcrumb" with a string that exceeds the character limit
     * should add the string to the breadcrumb list, but only the first n characters.
     */
    @Test
    public void addBreadcrumb_exceedsCharacterLimit() {
        BreadcrumbHelper breadcrumbHelper = new BreadcrumbHelper(5, new ModuleLog());
        breadcrumbHelper.addBreadcrumb("Test", 2, 5);
        Assert.assertEquals(list("Te"), breadcrumbHelper.getBreadcrumbs());
    }

    /**
     * "addBreadcrumb" with a string that exceeds the breadcrumb limit
     * should add the string to the breadcrumb list, but first remove the oldest breadcrumb.
     */
    @Test
    public void addBreadcrumb_exceedsLimit() {
        BreadcrumbHelper breadcrumbHelper = new BreadcrumbHelper(2, new ModuleLog());
        breadcrumbHelper.addBreadcrumb("Test", 3, 2);
        Assert.assertEquals(list("Tes"), breadcrumbHelper.getBreadcrumbs());
        breadcrumbHelper.addBreadcrumb("Doggy", 3, 2);
        Assert.assertEquals(list("Tes", "Dog"), breadcrumbHelper.getBreadcrumbs());
        breadcrumbHelper.addBreadcrumb("Geralt", 3, 2);
        Assert.assertEquals(list("Dog", "Ger"), breadcrumbHelper.getBreadcrumbs());
    }

    /**
     * "clearBreadcrumbs"
     * should clear the breadcrumb list.
     */
    @Test
    public void clearBreadcrumbs() {
        BreadcrumbHelper breadcrumbHelper = new BreadcrumbHelper(2, new ModuleLog());
        breadcrumbHelper.addBreadcrumb("Test", 3);
        Assert.assertEquals(list("Tes"), breadcrumbHelper.getBreadcrumbs());
        breadcrumbHelper.clearBreadcrumbs();
        Assert.assertEquals(list(), breadcrumbHelper.getBreadcrumbs());
    }

    private List<String> list(String... breadcrumbs) {
        if (breadcrumbs == null) {
            return new ArrayList<>();
        }
        return Arrays.asList(breadcrumbs);
    }
}
