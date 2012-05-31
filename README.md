Open Locast
===========

Open Locast is an Android application that works in conjunction with the [Open
Locast web platform][locastweb]. It is a framework for building location-based
media apps, with the ability to synchronize data to/from the web platform.

Out of the box, Open Locast gives you a few building blocks view and record
video content from your phone and publish it on the web. It supports common
features, such as tagging, favoriting, commenting, browsing by location, and
more.

Open Locast has come from numerous projects that we've worked on over the past
few years, so it still has a bit of cruft from these projects in its corners.
Our team is working hard to sweep out any remaining cruftbunnies and hopefully
this notice can be removed soon.
 
Server Communication
--------------------

The application communicates with the server using a public RESTful API,
allowing both authenticated an unauthenticated API requests.

The API's base URL is stored in the key `default_api_url` in
[strings.xml](res/values/strings.xml#default_api_url).

In debug mode (edit Constants.java) there is also a user-definable way of
setting the base URL which can be accessed by pressing "menu" when first
signing in at the login screen.

Built-in Updater
----------------

The app also has a built-in updater that checks in the background to see if
there's a new version of the app. It queries a URL for a JSON document that
describes the download location as well as the changes that were made between
versions. An example of the JSON document can be found in
[lca.json](extra/lca.json). 

The URL of the JSON document is stored in the key `app_update_url` in
[strings.xml](res/values/strings.xml#app_update_url).

This can be disabled by editing Constants.java

Dependencies
------------

Open Locast is built off many components, including ones developed in our lab.
As many as possible are included as jar files (with the specific commit of the
included jar detailed in the changelog). The ones that cannot be included as
jars can be linked in as Android libraries (which is known to be somewhat
tedious to set up).

*   [MEL ImageCache][]
*   [AppUpdateChecker][]
*   [android-mapviewballoons][] (included as submodule)
*   [MelAUtils][] (included)
*   [SimpleContentProvider][] (included)
*   [CWAC Adapter Wrapper][] (included)
*   [android-support-v4][] (included)
*   [Apache HTTP MIME][] (included)
*   [markdownj][] (included)

Publishing
----------

To publish this app, the Google maps keys in the various layout files need to
be set. To do this, run:

    ./set_maps_keys.sh KEY

where KEY is your own API key.

Additionally, you should change the namespaces for all the items defined in the
manifest so that they don't overlap with the ones used by our lab.

Translation
-----------

While any tool can be used to do translation, we have used [android2po][]
to convert to/from standard gettext .po files in conjunction with [Pootle][].

License
-------
Locast Android client  
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

[CWAC Adapter Wrapper]: https://github.com/commonsguy/cwac-adapter
[android-support-v4]: http://android-developers.blogspot.com/2011/03/fragments-for-all.html
[android2po]: https://github.com/miracle2k/android2po/
[Pootle]: http://translate.sourceforge.net/wiki/pootle
[MEL ImageCache]: https://github.com/mitmel/Android-Image-Cache
[SimpleContentProvider]: https://github.com/mitmel/SimpleContentProvider
[mel]: http://mobile.mit.edu/
[MelAUtils]: https://github.com/mitmel/MelAUtils
[AppUpdateChecker]: https://github.com/mitmel/AppUpdateChecker
[Apache HTTP MIME]: http://hc.apache.org/httpcomponents-client-ga/httpmime/
[markdownj]: http://code.google.com/p/markdownj/
[locastweb]: https://github.com/mitmel/Locast-Web-Core/
[android-mapviewballoons]: https://github.com/jgilfelt/android-mapviewballoons
