package ly.count.sdk.internal;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ModuleRatingTests extends BaseTestsCore {
    @Before
    public void setupEveryTest(){
    }

    @After
    public void cleanupEveryTests(){
    }

    public ModuleRatingCore.StarRatingPreferences CreatePreferences(){
        ModuleRatingCore.StarRatingPreferences srp = new ModuleRatingCore.StarRatingPreferences();
        srp.appVersion = "123abc";
        srp.automaticHasBeenShown = true;
        srp.automaticRatingShouldBeShown = true;
        srp.dialogTextDismiss = "123gdf";
        srp.dialogTextMessage = "fhfgh";
        srp.dialogTextTitle = "0-98";
        srp.disabledAutomaticForNewVersions = true;
        srp.isDialogCancellable = true;
        srp.isShownForCurrentVersion = true;
        srp.sessionAmount = 5673;
        srp.sessionLimit = 8976543;

        return srp;
    }

    public boolean RatingEqual(ModuleRatingCore.StarRatingPreferences first, ModuleRatingCore.StarRatingPreferences second){
        if(!first.appVersion.equals(second.appVersion)) return false;
        if(first.automaticHasBeenShown != second.automaticHasBeenShown) return false;
        if(first.automaticRatingShouldBeShown != second.automaticRatingShouldBeShown) return false;
        if(!first.dialogTextDismiss.equals(second.dialogTextDismiss)) return false;
        if(!first.dialogTextMessage.equals(second.dialogTextMessage)) return false;
        if(!first.dialogTextTitle.equals(second.dialogTextTitle)) return false;
        if(first.disabledAutomaticForNewVersions != second.disabledAutomaticForNewVersions) return false;
        if(first.isDialogCancellable != second.isDialogCancellable) return false;
        if(first.isShownForCurrentVersion != second.isShownForCurrentVersion) return false;
        if(first.sessionAmount != second.sessionAmount) return false;
        if(first.sessionLimit != second.sessionLimit) return false;

        return true;
    }

    @Test
    public void BasicSerializeDeserialize(){
        ModuleRatingCore.StarRatingPreferences srp = CreatePreferences();
        byte[] ser = srp.store();

        ModuleRatingCore.StarRatingPreferences res = new ModuleRatingCore.StarRatingPreferences();
        res.restore(ser);

        Assert.assertTrue(RatingEqual(srp, res));
    }
}
