Open Locast Core
================

Open Locast Core is an Android library that works in conjunction with the [Open
Locast web platform][locastwebcore]. It is a framework for building location-based
media apps, with the ability to synchronize data to/from the web platform.

Out of the box, Open Locast Core provides a data layer for data-driven apps.
Using [SimpleContentProvider][], it provides an easy-to-use database layer
which is exposed as a Content Provider. This library builds on top of
SimpleContentProvider by adding an account system, a synchronization framework,
some abstract classes that implement a bunch of boring-but-necessary things
like login / registration activities, and a media processing layer.

This library is intended to directly compliment [Open Locast Web
Core][locastwebcore], providing many similar data models and interfacing with
the API it exposes.

Open Locast Core has come from numerous projects that we've worked on over the
past few years, so it still has a bit of cruft from these projects in its
corners.  Our team is working hard to sweep out any remaining cruftbunnies and
hopefully this notice can be removed soon.

Example App & Building the Library
----------------------------------

As this is a fairly complex library, there is a complete example app which is
published as a separate repository. The example app contains this library as a
submodule, so you'll need to either get the example or the library as described
below, but you shouldn't need to do both.

You can get the example code by doing the following:

    git clone --recurse-submodules https://github.com/mitmel/Locast-Core-Android-Example.git

### or just the library

This library includes a submodule, so you'll want to get that too. You can get
both at the same time with the following command:

    git clone --recurse-submodules https://github.com/mitmel/Locast-Core-Android.git

### Import SimpleContentProvider as a library

Currently this project uses Android + Eclipse for building the project
(although Android + ant would probably work too). You'll need to load the
dependent module first as a separate project. In Eclipse, this is `File -> New
-> Other -> Android -> Android Project from Existing Code` and then select the
`SimpleContentProvider` directory. Make sure that when you right-click on the
project → properties → Android → Library, "Is Library" is checked.

### Load library

Once you have SimpleContentProvider loaded and building, you'll want to import
this library using the same import technique. Once it's imported, make sure that
SimpleContentProvider is loaded as a library by going to the same menu as
mentioned above, but adding SimpleContentProvider to the list of libraries.

The other libraries in libs/ should be automatically loaded. If they aren't and
Refresh doesn't pull them in, you may have an older version of the SDK. In
which case, you should upgrade.

Generally, it's best to build against the latest Android library as the build
target, especially as newer versions of the SDK will look for incompatible uses
of API calls on older versions of Android.


Server Communication
--------------------

The library communicates with the server using a public RESTful API, allowing
both authenticated and unauthenticated API requests.

The API's base URL is stored in your application's manifest. You can add it in
by adding a block similar to this to your `<application />` section of your
`AndroidManifest.xml`:

        <meta-data
            android:name="edu.mit.mobile.android.locast.base_url"
            android:value="http://example.com/openlocast/api/" />

Dependencies
------------

Open Locast Core is built off many components, including ones developed in our
lab.  As many as possible are included as jar files (with the specific commit
of the included jar detailed in the changelog). The ones that cannot be
included as jars can be linked in as Android libraries (which is known to be
somewhat tedious to set up).

*   [MelAUtils][] (included)
*   [SimpleContentProvider][] (included, git sub-module)
*   [android-support-v4][] (included)
*   [Apache HTTP MIME][] (included)

Translation
-----------

While any tool can be used to do translation, we have used [android2po][]
to convert to/from standard gettext .po files in conjunction with [Pootle][].

License
-------
Locast Android Core  
Copyright 2010-2012 [MIT Mobile Experience Lab][mel]

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
version 2 as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

[android-support-v4]: http://android-developers.blogspot.com/2011/03/fragments-for-all.html
[android2po]: https://github.com/miracle2k/android2po/
[Pootle]: http://translate.sourceforge.net/wiki/pootle
[SimpleContentProvider]: https://github.com/mitmel/SimpleContentProvider
[mel]: http://mobile.mit.edu/
[MelAUtils]: https://github.com/mitmel/MelAUtils
[Apache HTTP MIME]: http://hc.apache.org/httpcomponents-client-ga/httpmime/
[locastwebcore]: https://github.com/mitmel/Locast-Web-Core/
