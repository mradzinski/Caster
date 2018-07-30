/*
 * Copyright (c) 2018. DNA Software. All rights reserved.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mradzinski.casterexample;

import android.app.Application;

import com.google.android.gms.cast.LaunchOptions;
import com.mradzinski.caster.Caster;

import java.util.Locale;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        LaunchOptions launchOptions = new LaunchOptions.Builder()
                .setLocale(Locale.ENGLISH)
                .setRelaunchIfRunning(false)
                .build();

        Caster.configure(launchOptions);
    }
}
