Locast Abruzzo 
==============

Locast Abruzzo is an Android application that works in conjunction with the
Locast web platform. It provides a browsing interface to all the content on the
site, as well as 'favorite' integration to show a user's favorite content.

Server Communication
--------------------

The application communicates with the server using a public RESTful API,
allowing both authenticated an unauthenticated API requests.

The API's base URL is stored in the key `default_api_url` in
[strings.xml](res/values/strings.xml#default_api_url).

There is also a user-definable way of setting the base URL which can be
accessed by going to Locast Abruzzo Home → menu → login → menu → set locast
site.

Built-in Updater
----------------

The app also has a built-in updater that checks in the background to see if
there's a new version of the app. It queries a URL for a JSON document that
describes the download location as well as the changes that were made between
versions. An example of the JSON document can be found in
[lca.json](extra/lca.json). 

The URL of the JSON document is stored in the key `app_update_url` in
[strings.xml](res/values/strings.xml#app_update_url).

Dependencies
------------
*   [MEL ImageCache][]
*   [CWAC Task][] (included)
*   [CWAC Bus][] (included)
*   [CWAC Adapter Wrapper][] (included)
*   [android-support-v4][] (included)

Publishing
----------

To publish this app, the Google maps keys in the various layout files need to
be set. To do this, run:

    ./set_maps_keys.sh KEY

where KEY is either 'dev', 'prod' or your own API key.

Translation
-----------

While any tool can be used to do translation, we have used [android2po][]
to convert to/from standard gettext .po files in conjunction with [Pootle][].

License
-------

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

[CWAC Task]: https://github.com/commonsguy/cwac-task
[CWAC Bus]: https://github.com/commonsguy/cwac-bus
[CWAC Adapter Wrapper]: https://github.com/commonsguy/cwac-adapter
[android-support-v4]: http://android-developers.blogspot.com/2011/03/fragments-for-all.html
[android2po]: https://github.com/miracle2k/android2po/
[Pootle]: http://translate.sourceforge.net/wiki/pootle
[MEL ImageCache]: https://github.com/mitmel/Android-Image-Cache
