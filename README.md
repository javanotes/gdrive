# gdrive
#### *A sample project using Google drive integration*

A spring boot project to exercise google drive integration using Java client libraries.

Assumptions/Limitations:
- Only relatively small files (<5mb) have been tested for upload/download. Google format mimes (docx/xlsx etc) have not been tested
- The service layer junits (basically integration tests) use `installed application` style OAuth2 flow. Thus an auth token will
be downloaded the first time tests are run. This will require to open a link in browser, as printed in console to authenticate
google credentials
- The project does not have a mature UI, or rather any UI worth talking about. The runtime does, however, have a welcome controller to perform OAuth using google credentials
- No access security/session management has been developed. So the navigation flow is strictly defined to make it work as expected
- To access the api via a client like Postman, the auth token need to be set in the request header (can be found from the console log, post google authentication)

The service layer interface defined is [DocView.java](https://github.com/javanotes/gdrive/blob/master/src/main/java/com/docview/DocView.java). This facade defines the operations that can be done using the google java api.

#### End point REST api:

- > GET /api/files

return a collection of files and folders available (one level) in the root directory (My Drive). The items will have a `name` and
`id` property

- > GET /api/files/{id}

return a collection of files and folders available (one level) in the non-root directory specified by its `id`

- > GET /api/files/file/{id}

download a file (if exists) specified by its `id`

- > GET /api/files/file/search/{name}

download a file (if exists) specified by its `name`. For multiple matching results, the tie breaker is undefined. The file will be searched recursively in the complete drive tree (under construction)

- > POST /api/files/{path} multipart(file)

upload the multipart `file` to root (if no `path` specified), or to a directory `path`. Missing directories will be created automatically

- > PATCH /api/files/file/{id} multipart(file)

update the the contents of an existing file with the given `id`
