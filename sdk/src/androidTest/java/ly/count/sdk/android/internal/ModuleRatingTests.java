package ly.count.sdk.android.internal;

import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.util.List;

import ly.count.sdk.android.Config;
import ly.count.sdk.android.Countly;
import ly.count.sdk.internal.CrashImplCore;
import ly.count.sdk.internal.ModuleRatingCore;
import ly.count.sdk.internal.Storage;

public class ModuleRatingTests extends BaseTests {
    ModuleRating moduleRating;

    @Override
    protected ly.count.sdk.android.Config defaultConfig() throws Exception {
        return super.defaultConfig().enableFeatures(Config.Feature.StarRating);
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

    ModuleRatingCore.StarRatingPreferences LoadPreferences(ModuleRating module) throws Exception {
        return Whitebox.invokeMethod(module, "loadStarRatingPreferences");
    }

    void SavePreferences(ModuleRating module, ModuleRatingCore.StarRatingPreferences srp) throws Exception {
        Whitebox.invokeMethod(module, "saveStarRatingPreferences", srp);
    }

    /**
     * Make sure that the purge function works correctly
     */
    @Test
    public void purgeRatingStorage() throws Exception {
        setUpApplication(defaultConfig(), true);
        moduleRating = module(ModuleRating.class, false);

        ModuleRatingCore.StarRatingPreferences srpDefault = new ModuleRatingCore.StarRatingPreferences();

        ModuleRatingCore.StarRatingPreferences srp = CreatePreferences();

        //write some preferences to storage
        Storage.push(ctx, srp);

        //confirm that the same thing is returned
        ModuleRatingCore.StarRatingPreferences srpRes = LoadPreferences(moduleRating);
        Assert.assertTrue(RatingEqual(srp, srpRes));

        //erase those preferences
        moduleRating.PurgeRatingInfo();
        Storage.await();

        //make sure that now the defaults are returned
        ModuleRatingCore.StarRatingPreferences srpRes2 = LoadPreferences(moduleRating);
        Assert.assertTrue(RatingEqual(srpDefault, srpRes2));
    }

    /**
     * Test if the legacy import procedure works as expected
     */
    @Test
    public void testLegacyImport() throws Exception {
        //startup SDK
        setUpApplication(defaultConfig(), true);
        moduleRating = module(ModuleRating.class, false);

        //create set of values
        ModuleRatingCore.StarRatingPreferences srp = CreatePreferences();

        //get preferences
        SharedPreferences prefs = ctx.getContext().getSharedPreferences("COUNTLY_STORE", android.content.Context.MODE_PRIVATE);

        //transform it to string and save it
        String starStr = srp.toJSON().toString();
        prefs.edit().putString("STAR_RATING", starStr).apply();

        //migrate values
        Legacy.migrate(ctx);

        //compare values
        ModuleRatingCore.StarRatingPreferences srpLoaded = LoadPreferences(moduleRating);
        Assert.assertTrue(RatingEqual(srp, srpLoaded));
    }

    /**
     * Test the Save and Load calls
     */
    @Test
    public void SaveLoadStorage() throws Exception {
        //startup SDK
        setUpApplication(defaultConfig(), true);
        moduleRating = module(ModuleRating.class, false);

        //get defaults
        ModuleRatingCore.StarRatingPreferences srpDefault = new ModuleRatingCore.StarRatingPreferences();
        ModuleRatingCore.StarRatingPreferences srpLoaded = LoadPreferences(moduleRating);

        //make sure defaults are as expected
        Assert.assertTrue(RatingEqual(srpDefault, srpLoaded));

        //save new values
        ModuleRatingCore.StarRatingPreferences srpValues = CreatePreferences();
        SavePreferences(moduleRating, srpValues);

        //confirm we can load it
        ModuleRatingCore.StarRatingPreferences srpLoadedAgain = LoadPreferences(moduleRating);
        Assert.assertTrue(RatingEqual(srpValues, srpLoadedAgain));
    }

    /**
     * Make sure that accessors in the module get the expected fields
     */
    @Test
    public void accessStorageAccessors() throws Exception {
        //startup SDK
        setUpApplication(defaultConfig(), true);
        moduleRating = module(ModuleRating.class, false);

        ModuleRatingCore.StarRatingPreferences srpDefault = new ModuleRatingCore.StarRatingPreferences();
        ModuleRatingCore.StarRatingPreferences srpValues = CreatePreferences();
        ModuleRatingCore.StarRatingPreferences srpLoaded;

        String [] values = new String[]{"asd", "ert", "poi"};
        int[] numbers = new int[] {6489, 8564, 38376};

        moduleRating.setShowDialogAutomatically(true);
        Assert.assertEquals(true, LoadPreferences(moduleRating).automaticRatingShouldBeShown);
        moduleRating.setShowDialogAutomatically(false);
        Assert.assertEquals(false, LoadPreferences(moduleRating).automaticRatingShouldBeShown);

        moduleRating.setIfRatingDialogIsCancellable(true);
        Assert.assertEquals(true, LoadPreferences(moduleRating).isDialogCancellable);
        moduleRating.setIfRatingDialogIsCancellable(false);
        Assert.assertEquals(false, LoadPreferences(moduleRating).isDialogCancellable);

        moduleRating.setStarRatingDisableAskingForEachAppVersion(true);
        Assert.assertEquals(true, LoadPreferences(moduleRating).disabledAutomaticForNewVersions);
        moduleRating.setStarRatingDisableAskingForEachAppVersion(false);
        Assert.assertEquals(false, LoadPreferences(moduleRating).disabledAutomaticForNewVersions);

        Assert.assertNotEquals(numbers[0], LoadPreferences(moduleRating).sessionLimit);
        srpLoaded = LoadPreferences(moduleRating);
        srpLoaded.sessionLimit = numbers[0];
        SavePreferences(moduleRating, srpLoaded);
        Assert.assertEquals(numbers[0], moduleRating.getAutomaticStarRatingSessionLimit());

        Assert.assertNotEquals(numbers[1], LoadPreferences(moduleRating).sessionAmount);
        srpLoaded = LoadPreferences(moduleRating);
        srpLoaded.sessionAmount = numbers[1];
        SavePreferences(moduleRating, srpLoaded);
        Assert.assertEquals(numbers[1], moduleRating.getCurrentVersionsSessionCount());
        moduleRating.clearAutomaticStarRatingSessionCount();
        Assert.assertNotEquals(numbers[1], LoadPreferences(moduleRating).sessionAmount);

        srpLoaded = LoadPreferences(moduleRating);
        Assert.assertNotEquals(numbers[2], srpLoaded.sessionLimit);
        Assert.assertNotEquals(values[0], srpLoaded.dialogTextTitle);
        Assert.assertNotEquals(values[1], srpLoaded.dialogTextMessage);
        Assert.assertNotEquals(values[2], srpLoaded.dialogTextDismiss);
        moduleRating.setStarRatingInitConfig(numbers[2], values[0], values[1], values[2]);

        srpLoaded = LoadPreferences(moduleRating);
        Assert.assertEquals(numbers[2], srpLoaded.sessionLimit);
        Assert.assertEquals(values[0], srpLoaded.dialogTextTitle);
        Assert.assertEquals(values[1], srpLoaded.dialogTextMessage);
        Assert.assertEquals(values[2], srpLoaded.dialogTextDismiss);
    }

    /**
     * Make sure that accessors provided by the SDK get the expected fields
     */
    @Test
    public void accessStorageSDKAccessors() throws Exception {
        //startup SDK
        setUpApplication(defaultConfig(), true);
        moduleRating = module(ModuleRating.class, false);

        ModuleRatingCore.StarRatingPreferences srpDefault = new ModuleRatingCore.StarRatingPreferences();
        ModuleRatingCore.StarRatingPreferences srpValues = CreatePreferences();
        ModuleRatingCore.StarRatingPreferences srpLoaded;

        ModuleRating.Ratings ratings = Countly.Ratings();

        String [] values = new String[]{"asd", "ert", "poi"};
        int[] numbers = new int[] {6489, 8564, 38376};

        ratings.setIfStarRatingShownAutomatically(true);
        Assert.assertEquals(true, LoadPreferences(moduleRating).automaticRatingShouldBeShown);
        ratings.setIfStarRatingShownAutomatically(false);
        Assert.assertEquals(false, LoadPreferences(moduleRating).automaticRatingShouldBeShown);

        ratings.setIfStarRatingDialogIsCancellable(true);
        Assert.assertEquals(true, LoadPreferences(moduleRating).isDialogCancellable);
        ratings.setIfStarRatingDialogIsCancellable(false);
        Assert.assertEquals(false, LoadPreferences(moduleRating).isDialogCancellable);

        ratings.setStarRatingDisableAskingForEachAppVersion(true);
        Assert.assertEquals(true, LoadPreferences(moduleRating).disabledAutomaticForNewVersions);
        ratings.setStarRatingDisableAskingForEachAppVersion(false);
        Assert.assertEquals(false, LoadPreferences(moduleRating).disabledAutomaticForNewVersions);

        Assert.assertNotEquals(numbers[0], LoadPreferences(moduleRating).sessionLimit);
        ratings.setAutomaticStarRatingSessionLimit(numbers[0]);
        Assert.assertEquals(numbers[0], ratings.getAutomaticStarRatingSessionLimit());

        Assert.assertNotEquals(numbers[1], LoadPreferences(moduleRating).sessionAmount);
        srpLoaded = LoadPreferences(moduleRating);
        srpLoaded.sessionAmount = numbers[1];
        SavePreferences(moduleRating, srpLoaded);
        Assert.assertEquals(numbers[1], ratings.getStarRatingsCurrentVersionsSessionCount());
        ratings.clearAutomaticStarRatingSessionCount();
        Assert.assertNotEquals(numbers[1], LoadPreferences(moduleRating).sessionAmount);

        srpLoaded = LoadPreferences(moduleRating);
        Assert.assertNotEquals(values[0], srpLoaded.dialogTextTitle);
        Assert.assertNotEquals(values[1], srpLoaded.dialogTextMessage);
        Assert.assertNotEquals(values[2], srpLoaded.dialogTextDismiss);
        ratings.setStarRatingDialogTexts(values[0], values[1], values[2]);

        srpLoaded = LoadPreferences(moduleRating);
        Assert.assertEquals(values[0], srpLoaded.dialogTextTitle);
        Assert.assertEquals(values[1], srpLoaded.dialogTextMessage);
        Assert.assertEquals(values[2], srpLoaded.dialogTextDismiss);
    }


    public void RegisterSession(){

    }

    /**
     * Confirm that the default returned preferences are as expected
     */
    @Test
    public void defaultStorageTest() throws Exception {
        //startup SDK
        setUpApplication(defaultConfig());
        moduleRating = module(ModuleRating.class, false);

        //purge current info
        ctx.getSDK().storablePurge(ctx, ModuleRating.storableStoragePrefix);

        //make sure the defaults are as expected
        ModuleRatingCore.StarRatingPreferences srpDefault = new ModuleRatingCore.StarRatingPreferences();
        ModuleRatingCore.StarRatingPreferences srpRes = LoadPreferences(moduleRating);
        Assert.assertTrue(RatingEqual(srpDefault, srpRes));
    }
}
