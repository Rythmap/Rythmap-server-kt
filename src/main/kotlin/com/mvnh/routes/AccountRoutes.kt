package com.mvnh.routes

import com.mvnh.entities.account.*
import com.mvnh.entities.music.yandex.YandexTrack
import com.mvnh.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bson.Document
import java.io.File
import kotlin.random.Random

fun Route.accountRoutes() {
    val mongoDB = getMongoDatabase()
    val accountsCollection = mongoDB.getCollection("accounts")
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")

    route("/account") {
        post("/register") {
            val account = call.receive<AccountRegister>()

            if (account.nickname.isEmpty() || account.password.isEmpty() || account.email.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "Empty fields") // 400
                return@post
            } else {
                if (checkEmailExists(accountsCollection, account.email)) {
                    call.respond(HttpStatusCode.Conflict, "Email already exists") // 409
                    return@post
                } else if (checkNicknameExists(accountsCollection, account.nickname)) {
                    call.respond(HttpStatusCode.Conflict, "Nickname already exists")
                    return@post
                } else {
                    if (!validateEmail(account.email)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid email") // 400
                        return@post
                    } else if (!validatePassword(account.password)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid password")
                        return@post
                    } else if (!validateNickname(account.nickname)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid nickname")
                        return@post
                    } else {
                        val document = createAccountDocument(account)

                        accountsCollection.insertOne(document)
                        call.respond(HttpStatusCode.OK, AccountAuthResponse(document["account_id"].toString(), document["token"].toString())) // 200
                    }
                }
            }
        }

        post("/login") {
            val credentials = call.receive<AccountLogin>()

            if (credentials.login.isEmpty() || credentials.password.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "Empty fields") // 400
                return@post
            } else {
                if (!validateUserCredentials(accountsCollection, credentials)) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid credentials") // 401
                    return@post
                } else {
                    val document = accountsCollection.find(
                        Document(
                            if ("@" in credentials.login) "email" else "nickname", credentials.login
                        )
                    ).first()

                    call.respond(HttpStatusCode.OK, AccountAuthResponse(document?.get("account_id").toString(), document?.get("token").toString())) // 200
                }
            }
        }

        route("/info") {
            get("/public") {
                val nickname = call.parameters["nickname"]
                if (nickname == null) {
                    call.respond(HttpStatusCode.BadRequest, "Nickname not provided")
                    return@get
                } else {
                    val document = accountsCollection.find(Document("nickname", nickname)).first()
                    if (document == null) {
                        call.respond(HttpStatusCode.NotFound, "Account not found")
                        return@get
                    } else {
                        val visibleNameDocument = document["visible_name"] as Document?
                        val lastTracksDocument = document["last_tracks"] as Document?
                        val yandexLastTrackDocument = lastTracksDocument?.get("yandex_track") as Document?

                        call.application.environment.log.info(document.toString())
                        call.respond(HttpStatusCode.OK,
                            AccountInfoPublic(
                                accountID = document["account_id"] as String,
                                nickname = document["nickname"] as String,
                                visibleName = AccountVisibleName(
                                    name = visibleNameDocument?.get("name") as String?,
                                    surname = visibleNameDocument?.get("surname") as String?
                                ),
                                avatar = document["avatar"] as String?,
                                banner = document["banner"] as String?,
                                musicPreferences = document["music_preferences"] as List<String>?,
                                otherPreferences = document["other_preferences"] as List<String>?,
                                lastTracks = AccountLastTracks(
                                    yandexTrack = if (yandexLastTrackDocument != null) {
                                        YandexTrack(
                                            trackId = yandexLastTrackDocument["track_id"] as Int,
                                            title = yandexLastTrackDocument["title"] as String,
                                            artist = yandexLastTrackDocument["artist"] as String,
                                            img = yandexLastTrackDocument["img"] as String,
                                            duration = yandexLastTrackDocument["duration"] as Int,
                                            minutes = yandexLastTrackDocument["minutes"] as Int,
                                            seconds = yandexLastTrackDocument["seconds"] as Int,
                                            album = yandexLastTrackDocument["album"] as Int,
                                            downloadLink = yandexLastTrackDocument["download_link"] as String
                                        )
                                    } else {
                                        null
                                    }
                                ),
                                friends = document["friends"] as List<String>?,
                                friendRequests = document["friend_requests"] as List<String>?,
                                about = document["about"] as String?,
                                createdAt = document["created_at"].toString()
                            )
                        )
                    }
                }
            }

            get("/private") {
                val token = call.parameters["token"]
                if (token == null) {
                    call.respond(HttpStatusCode.BadRequest, "Token not provided")
                    return@get
                } else {
                    val document = accountsCollection.find(Document("token", token)).first()
                    if (document == null) {
                        call.respond(HttpStatusCode.NotFound, "Token not found")
                        return@get
                    } else {
                        val visibleNameDocument = document["visible_name"] as Document?
                        val lastTracksDocument = document["last_tracks"] as Document?
                        val yandexLastTrackDocument = lastTracksDocument?.get("yandex_track") as Document?

                        call.respond(
                            AccountInfoPrivate(
                                accountID = document["account_id"] as String,
                                token = document["token"] as String,
                                nickname = document["nickname"] as String,
                                visibleName = AccountVisibleName(
                                    name = visibleNameDocument?.get("name") as String?,
                                    surname = visibleNameDocument?.get("surname") as String?
                                ),
                                avatar = document["avatar"] as String?,
                                banner = document["banner"] as String?,
                                musicPreferences = document["music_preferences"] as List<String>?,
                                otherPreferences = document["other_preferences"] as List<String>?,
                                lastTracks = AccountLastTracks(
                                    yandexTrack = if (yandexLastTrackDocument != null) {
                                        YandexTrack(
                                            trackId = yandexLastTrackDocument["track_id"] as Int,
                                            title = yandexLastTrackDocument["title"] as String,
                                            artist = yandexLastTrackDocument["artist"] as String,
                                            img = yandexLastTrackDocument["img"] as String,
                                            duration = yandexLastTrackDocument["duration"] as Int,
                                            minutes = yandexLastTrackDocument["minutes"] as Int,
                                            seconds = yandexLastTrackDocument["seconds"] as Int,
                                            album = yandexLastTrackDocument["album"] as Int,
                                            downloadLink = yandexLastTrackDocument["download_link"] as String
                                        )
                                    } else {
                                        null
                                    }
                                ),
                                friends = document["friends"] as List<String>?,
                                about = document["about"] as String?,
                                email = document["email"] as String,
                                createdAt = document["created_at"].toString()
                            )
                        )
                    }
                }
            }

            get("/media/{type}") {
                val id = call.parameters["id"]

                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "Media ID not provided")
                    return@get
                } else {
                    val document = if (call.parameters["type"] == "avatar") {
                        accountsCollection.find(Document("avatar", id)).first()
                    } else {
                        accountsCollection.find(Document("banner", id)).first()
                    }
                    if (document == null) {
                        call.respond(HttpStatusCode.NotFound, "Media not found")
                        return@get
                    } else {
                        if (call.parameters["type"] == "avatar") {
                            val avatar = document["avatar"] as String?
                            if (avatar != null) {
                                val filePath = if (isWindows) {
                                    "C:\\Users\\13mvnh\\Code\\Kotlin\\Rythmap-server\\src\\main\\kotlin\\com\\mvnh\\media\\avatars\\$avatar.jpeg"
                                } else {
                                    "/home/Rythmap-server-ktor/media/avatars/$avatar.jpeg"
                                }

                                val file = File(filePath)
                                if (file.exists()) {
                                    call.respondFile(file)
                                } else {
                                    call.respond(HttpStatusCode.NotFound, "Avatar not found")
                                }
                            } else {
                                call.respond(HttpStatusCode.NotFound, "Avatar not found")
                            }
                        } else if (call.parameters["type"] == "banner") {
                            val banner = document["banner"] as String?
                            if (banner != null) {
                                val filePath = if (isWindows) {
                                    "C:\\Users\\13mvnh\\Code\\Kotlin\\Rythmap-server\\src\\main\\kotlin\\com\\mvnh\\media\\banners\\$banner.jpeg"
                                } else {
                                    "/home/Rythmap-server-ktor/media/banners/$banner.jpeg"
                                }

                                val file = File(filePath)
                                if (file.exists()) {
                                    call.respondFile(file)
                                } else {
                                    call.respond(HttpStatusCode.NotFound, "Banner not found")
                                }
                            } else {
                                call.respond(HttpStatusCode.NotFound, "Banner not found")
                            }
                        } else {
                            call.respond(HttpStatusCode.BadRequest, "Invalid media type")
                            return@get
                        }
                    }
                }
            }
        }

        delete("/delete") {
            val login = call.parameters["login"]
            val password = call.parameters["password"]

            if (login == null || password == null) {
                call.respond(HttpStatusCode.BadRequest, "Empty fields")
                return@delete
            } else {
                if (!validateUserCredentials(accountsCollection, AccountLogin(login, password))) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                    return@delete
                } else {
                    val document = accountsCollection.find(
                        Document(
                            if ("@" in login) "email" else "nickname", login
                        )
                    ).first()

                    if (document != null) {
                        accountsCollection.deleteOne(document)
                        call.respond(HttpStatusCode.OK, "Account deleted")
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Account not found")
                    }
                }
            }
        }

        route("/update") {
            post("/info") {
                val account = call.receive<AccountUpdateInfo>()

                val document = accountsCollection.find(Document("token", account.token)).first()
                if (document == null) {
                    call.respond(HttpStatusCode.NotFound, "Token not found")
                    return@post
                } else {
                    accountsCollection.updateOne(
                        Document("token", account.token),
                        Document("\$set", Document("visible_name.name", account.visibleName?.name))
                    )
                    accountsCollection.updateOne(
                        Document("token", account.token),
                        Document("\$set", Document("visible_name.surname", account.visibleName?.surname))
                    )
                    accountsCollection.updateOne(
                        Document("token", account.token),
                        Document("\$set", Document("music_preferences", account.musicPreferences))
                    )
                    accountsCollection.updateOne(
                        Document("token", account.token),
                        Document("\$set", Document("other_preferences", account.otherPreferences))
                    )
                    accountsCollection.updateOne(
                        Document("token", account.token),
                        Document("\$set", Document("about", account.about))
                    )
                    call.respond(HttpStatusCode.OK, "Account updated")
                }
            }

            post("/nickname") {
                val account = call.receive<AccountUpdateNickname>()

                val document = accountsCollection.find(Document("token", account.token)).first()

                if (document == null) {
                    call.respond(HttpStatusCode.NotFound, "Token not found")
                    return@post
                } else {
                    if (!validateNickname(account.newNickname)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid nickname")
                        return@post
                    } else if (checkNicknameExists(accountsCollection, account.newNickname)) {
                        call.respond(HttpStatusCode.Conflict, "Nickname already exists")
                        return@post
                    } else {
                        accountsCollection.updateOne(
                            Document("token", account.token),
                            Document("\$set", Document("nickname", account.newNickname))
                        )
                        call.respond(HttpStatusCode.OK, "Nickname changed")
                    }
                }
            }

            post("/password") {
                val account = call.receive<AccountUpdatePassword>()

                val document = accountsCollection.find(Document("nickname", account.nickname)).first()

                if (document == null) {
                    call.respond(HttpStatusCode.NotFound, "Account not found")
                    return@post
                } else {
                    if (!validatePassword(account.newPassword)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid new password")
                        return@post
                    } else {
                        if (validateUserCredentials(
                                accountsCollection,
                                AccountLogin(account.nickname, account.currentPassword)
                            )
                        ) {
                            accountsCollection.updateOne(
                                Document("nickname", account.nickname),
                                Document("\$set", Document("password", hashPassword(account.newPassword)))
                            )
                            call.respond(HttpStatusCode.OK, "Password changed")
                        } else {
                            call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                        }
                    }
                }
            }

            post("/media/{type}") {
                val token = call.parameters["token"]

                val multipart = call.receiveMultipart()
                val part = multipart.readPart() as PartData.FileItem
                if (token == null) {
                    call.respond(HttpStatusCode.BadRequest, "Token not provided")
                    return@post
                } else {
                    val document = accountsCollection.find(Document("token", token)).first()
                    if (document == null) {
                        call.respond(HttpStatusCode.NotFound, "Token not found")
                        return@post
                    } else {
                        val validContentTypes = listOf(ContentType.Image.JPEG, ContentType.Image.PNG)
                        if (part.contentType !in validContentTypes) {
                            call.respond(HttpStatusCode.BadRequest, "Invalid file type")
                            return@post
                        } else { // need to check if file is not too big but not sure how
                            val fileName = "${Random.nextInt(0, 2147483646)}.jpeg"
                            if (call.parameters["type"] == "avatar") {
                                val filePath: String = if (isWindows) {
                                    "C:\\Users\\13mvnh\\Code\\Kotlin\\Rythmap-server\\src\\main\\kotlin\\com\\mvnh\\media\\avatars\\$fileName"
                                } else {
                                    "/home/Rythmap-server-ktor/media/avatars/$fileName"
                                }

                                val file = File(filePath)
                                part.streamProvider().use { its ->
                                    file.outputStream().buffered().use {
                                        its.copyTo(it)
                                    }
                                }

                                accountsCollection.updateOne(
                                    Document("token", token),
                                    Document("\$set", Document("avatar", fileName.replace(".jpeg", "")))
                                )
                                call.respond(HttpStatusCode.OK, "Avatar updated")
                            } else if (call.parameters["type"] == "banner") {
                                val filePath = if (isWindows) {
                                    "C:\\Users\\13mvnh\\Code\\Kotlin\\Rythmap-server\\src\\main\\kotlin\\com\\mvnh\\media\\banners\\$fileName"
                                } else {
                                    "/home/Rythmap-server-ktor/media/banners/$fileName"
                                }

                                val file = File(filePath)
                                part.streamProvider().use { its ->
                                    file.outputStream().buffered().use {
                                        its.copyTo(it)
                                    }
                                }

                                accountsCollection.updateOne(
                                    Document("token", token),
                                    Document("\$set", Document("banner", fileName.replace(".jpeg", "")))
                                )
                                call.respond(HttpStatusCode.OK, "Banner updated")
                            } else {
                                call.respond(HttpStatusCode.BadRequest, "Invalid media type")
                                return@post
                            }
                        }
                    }
                }
            }
        }
    }
}