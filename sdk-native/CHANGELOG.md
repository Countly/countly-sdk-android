## 26.1.6
* Updated the bundled Breakpad crash reporter to latest.

* Mitigated an issue where native crashes could not be symbolicated on devices with a 4 KB memory page size when the crashing library was built for 16 KB page sizes, as the library was reported without its build identifier.
