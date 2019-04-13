# gdrive
#### *A sample project for Google drive integration*

A spring boot project to exercise google drive integration using Java client libraries.

Assumptions/Limitations:
- Only relatively small files (<5mb) can be used for upload/download. As the project uses direct download
- The service layer junits (basically integration tests) use `installed application` style OAuth2 flow. Thus an auth token will
be downloaded the first time tests are run. This will require to open a link in browser, as printed in console to authenticate
google credentials
- The project does not have a mature UI, or rather any UI worth talking about. The runtime does, however, have a welcome controller to 
perform OAuth using google credentials
- No access security has been developed yet. So the navigation flow is strictly defined to make it work as expected
