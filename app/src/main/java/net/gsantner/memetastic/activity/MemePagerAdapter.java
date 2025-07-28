/*#######################################################
 *
 *   Maintained 2016-2023 by Gregor Santner <gsantner AT mailbox DOT org>
 *
 *   License of this file: GNU GPLv3
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
#########################################################*/
package net.gsantner.memetastic.activity;


import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.PagerAdapter;

public class MemePagerAdapter extends FragmentPagerAdapter {
    int _totalCount;
    String[] _pageTitles;


    public MemePagerAdapter(FragmentManager fm, int totalCount, String[] pageTitles) {
        super(fm);
        _totalCount = totalCount;
        _pageTitles = pageTitles;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return _pageTitles[position];
    }

    @Override
    public Fragment getItem(int i) {
        return MemeFragment.newInstance(i);
    }

    @Override
    public int getCount() {
        return _totalCount;
    }

    @Override
    public int getItemPosition(Object object) {
        return PagerAdapter.POSITION_NONE;
    }
}
