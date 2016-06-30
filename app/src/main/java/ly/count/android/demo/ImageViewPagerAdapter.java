package ly.count.android.demo;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.support.v13.app.FragmentPagerAdapter;

/**
 * Created by techuz on 28/6/16.
 */
public class ImageViewPagerAdapter extends FragmentPagerAdapter {

    private Context _context;
    public static int totalPage = 4;

    public ImageViewPagerAdapter(Context context, FragmentManager fm) {
        super(fm);
        _context = context;

    }

    @Override
    public Fragment getItem(int position) {
        Fragment f = new Fragment();
        switch (position) {
            case 0:
                f = new EventFragment();
                break;
            case 1:
                f = new ExceptionFragment();
                break;
            case 2:
                f = new ProfileFragment();
                break;
            case 3:
                f = new LocationFragment();
                break;
        }
        return f;
    }

    @Override
    public int getCount() {
        return totalPage;
    }
}
