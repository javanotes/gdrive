# gdrive
#### Google Drive Java integration

A spring boot project to exercise google drive integration using Java client libraries. This is mostly expected to be the backend service for a Drive Manager responsive UI/SPA frontend. The project has been been registered by the name ``Elementary`` in Google dev console. It requires two OAuth consent from user for managing Drive files.

The application will run on a default port of `8088` in a microservice style, using an embedded Jetty server. After start, the OAuth handshake needs to be performed by opening the application root ``http://localhost:8088`` in the browser. The page will redirect to Google secure authentication. and on successful authentication and consent, a `welcome` page will be displayed listing the root files in a json format.

Scope:
- Only relatively small files (<5mb) have been tested for upload/download. Does not support Google format mime types (docx/xlsx etc) have not been tested
- The service layer junits (basically integration tests) use `installed application` style OAuth2 flow. Thus an auth token will
be downloaded the first time tests are run. This will require to open a link in browser, as printed in console to authenticate
google credentials
- The project does not have a mature UI, or rather any UI worth talking about. The runtime does, however, have a welcome controller to perform OAuth using google credentials
- No comprehensive web security/session management has been developed. So the navigation flow is strictly defined to make it work as expected
- To access the REST api via a client like Postman, however, a `Bearer` token validation is required. The OAuth token will be printed in console and need to be provided as an `Authorization` header in the request.

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

- > POST /api/files/file?path= multipart(file) 

upload the multipart `file` to root (if no `path` specified), or to a directory `path`. Missing directories will be created automatically

- > PATCH /api/files/file/{id} multipart(file)

update the the contents of an existing file with the given `id`

The general response JSON structure is
```
{
    "name": "Knowledge",
    "id": "1xYpQZhC4GLELNH9G37CvQUkLvP98-uLw",
    "dirs": [],
    "files": [
        {
            "name": "Learning-Spark-Lightning-Fast-Data-Analysis.pdf",
            "id": "11Ii76RPXau1qUVwlJKfqvxAPt8dJjkEA"
        }
    ]
}
```
#### Proposed UI flow
1. User login -> Google OAuth handshake -> save auth token
2. Welcome page -> ``GET /api/files`` -- list all files and folder at root level using UI treeview
3. User clicks a folder -> ``GET /api/files/{id}`` -- list all files and folder under that folder
4. User clicks a file -> ``GET /api/files/file/{id}`` -- download file
5. Upload file control -> ``POST api/files/{}`` -- upload file, creating intermediate folders as necessary
