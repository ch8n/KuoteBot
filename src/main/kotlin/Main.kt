import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.JpegWriter
import dev.ch_n.ReelScript.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jcodec.api.awt.AWTSequenceEncoder
import org.jcodec.common.io.NIOUtils
import org.jcodec.common.model.Rational
import org.mp4parser.Container
import org.mp4parser.muxer.FileDataSourceImpl
import org.mp4parser.muxer.Movie
import org.mp4parser.muxer.builder.DefaultMp4Builder
import org.mp4parser.muxer.container.mp4.MovieCreator
import org.mp4parser.muxer.tracks.AACTrackImpl
import org.mp4parser.muxer.tracks.ClippedTrack
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.font.TextAttribute
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.AttributedString
import java.util.*
import kotlin.math.roundToInt


const val DIRECTORY_OUTPUT = "output"
const val DIRECTORY_IMAGES = "$DIRECTORY_OUTPUT/images"
const val DIRECTORY_NO_AUDIO = "$DIRECTORY_OUTPUT/no-audio"
const val DIRECTORY_WITH_AUDIO = "$DIRECTORY_OUTPUT/with-audio"

class UnsplashResponse : ArrayList<UnsplashResponse.UnsplashResponseItem>() {
    data class UnsplashResponseItem(
        @SerializedName("urls")
        val urls: Urls,
    ) {
        data class Urls(
            @SerializedName("full")
            val full: String, // https://images.unsplash.com/photo-1664574654521-7faf33eb9086?crop=entropy&cs=tinysrgb&fm=jpg&ixid=MnwzNjkxODV8MXwxfGFsbHwxfHx8fHx8Mnx8MTY2NDkxMDE3OA&ixlib=rb-1.2.1&q=80
            @SerializedName("raw")
            val raw: String, // https://images.unsplash.com/photo-1664574654521-7faf33eb9086?ixid=MnwzNjkxODV8MXwxfGFsbHwxfHx8fHx8Mnx8MTY2NDkxMDE3OA&ixlib=rb-1.2.1
            @SerializedName("regular")
            val regular: String, // https://images.unsplash.com/photo-1664574654521-7faf33eb9086?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=MnwzNjkxODV8MXwxfGFsbHwxfHx8fHx8Mnx8MTY2NDkxMDE3OA&ixlib=rb-1.2.1&q=80&w=1080
            @SerializedName("small")
            val small: String, // https://images.unsplash.com/photo-1664574654521-7faf33eb9086?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=MnwzNjkxODV8MXwxfGFsbHwxfHx8fHx8Mnx8MTY2NDkxMDE3OA&ixlib=rb-1.2.1&q=80&w=400
            @SerializedName("small_s3")
            val smallS3: String, // https://s3.us-west-2.amazonaws.com/images.unsplash.com/small/photo-1664574654521-7faf33eb9086
            @SerializedName("thumb")
            val thumb: String // https://images.unsplash.com/photo-1664574654521-7faf33eb9086?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=MnwzNjkxODV8MXwxfGFsbHwxfHx8fHx8Mnx8MTY2NDkxMDE3OA&ixlib=rb-1.2.1&q=80&w=200
        )
    }
}

suspend fun unsplashImages(): List<String> {
    val url = "https://api.unsplash.com/photos/?client_id=${BuildConfig.UNSPLASH_API_KEY}"
    val response = getResponseString(url)
    val data = Gson().fromJson(response, UnsplashResponse::class.java)
    return data.map { it.urls.regular }
}

suspend fun getResponseString(url: String): String = with(Dispatchers.IO) {
    return@with kotlin.runCatching {
        val okHttpClient = OkHttpClient()
        val imageRequest = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(imageRequest).execute()
        response.body?.string()
    }.getOrNull() ?: ""
}

data class AnimeResponse(
    @SerializedName("images")
    val images: List<Image>
) {
    data class Image(
        @SerializedName("dominant_color")
        val dominantColor: String, // #282831
        @SerializedName("source")
        val source: String, // https://www.pixiv.net/en/artworks/84060246
        @SerializedName("url")
        val url: String, // https://cdn.waifu.im/7529.jpg
    )
}

suspend fun getAnimeImages(): List<String> {
    val url = "https://api.waifu.im/random/?is_nsfw=false&gif=false&many=true&full=false"
    val response = getResponseString(url)
    val data = Gson().fromJson(response, AnimeResponse::class.java)
    return data.images.map { it.url }
}

val sampleImages = listOf(
    "https://images.unsplash.com/photo-1518837321959-58dfc718abcf?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=1170&q=80",
    "https://images.unsplash.com/photo-1664575600850-c4b712e6e2bf?ixlib=rb-1.2.1&ixid=MnwxMjA3fDF8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=687&q=80",
    "https://images.unsplash.com/photo-1664764118950-ab69f4c40c82?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=735&q=80",
    "https://images.unsplash.com/photo-1664787862050-898bece221b8?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=687&q=80",
    "https://images.unsplash.com/photo-1664740688843-0e8ad76b07a8?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=1074&q=80",
    "https://images.unsplash.com/photo-1664710696502-69589bfc2ef6?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=687&q=80",
    "https://images.unsplash.com/photo-1664739635995-e56d12cdd4ee?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=688&q=80",
    "https://images.unsplash.com/photo-1664764119004-999a3f80a1b8?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=1074&q=80",
    "https://images.unsplash.com/photo-1664721203281-8547b46c7bc6?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=686&q=80",
    "https://images.unsplash.com/photo-1664451077966-2f924721ed88?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=687&q=80",
    "https://images.unsplash.com/photo-1664644882862-9884db2dd5b8?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=764&q=80"
)


class AnimeQuoteResponse : ArrayList<AnimeQuoteResponse.AnimeQuoteResponseItem>() {
    data class AnimeQuoteResponseItem(
        @SerializedName("anime")
        val anime: String, // Guilty Crown
        @SerializedName("character")
        val character: String, // Gai Tsutsugami
        @SerializedName("quote")
        val quote: String // If you value your peaceful everyday life, protect it for yourself.
    )
}

fun getRemoteQuote(): List<String> = with(Dispatchers.IO) {
    return@with kotlin.runCatching {
        val url = "https://animechan.vercel.app/api/quotes"
        val okHttpClient = OkHttpClient()
        val imageRequest = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(imageRequest).execute()
        val responseString = response.body?.string() ?: ""
        val data = Gson().fromJson(responseString, AnimeQuoteResponse::class.java)
        data.map { "${it.quote} - ${it.character},${it.anime}" }
    }.getOrNull() ?: emptyList()
}

val sampleQuotes = listOf(
    "“When I despair, I remember that all through history the way of truth and love have always won. There have been tyrants and murderers, and for a time, they can seem invincible, but in the end, they always fall. Think of it--always.”\n" +
            "― Mahatma Gandhi",
    "“Every man has his secret sorrows which the world knows not; and often times we call a man cold when he is only sad.”\n" +
            "― Henry Wadsworth Longfellow",
    "“You cannot protect yourself from sadness without protecting yourself from happiness.”\n" +
            "― Jonathan Safran Foer",
    "“What you must understand about me is that I’m a deeply unhappy person.”\n" +
            "― John Green, Looking for Alaska",
    "“Imagine smiling after a slap in the face. Then think of doing it twenty-four hours a day.”\n" +
            "― Markus Zusak, The Book Thief",
    "“Tears are words that need to be written.”\n" +
            "― Paulo Coelho",
    "“there are two types of people in the world: those who prefer to be sad among others, and those who prefer to be sad alone.”\n" +
            "― Nicole Krauss, The History of Love",
    "“Why do beautiful songs make you sad?' 'Because they aren't true.' 'Never?' 'Nothing is beautiful and true.”\n" +
            "― Jonathan Safran Foer, Extremely Loud & Incredibly Close",
    "“I waste at least an hour every day lying in bed. Then I waste time pacing. I waste time thinking. I waste time being quiet and not saying anything because I'm afraid I'll stutter.”\n" +
            "― Ned Vizzini, It's Kind of a Funny Story"
)


data class Content(
    val imageUrl: String,
    val quote: String,
    val audioPath: String
)

suspend fun main() = runBlocking {

    val images = getAnimeImages().ifEmpty { unsplashImages() }.ifEmpty { sampleImages }
    val quotes = getRemoteQuote().ifEmpty { sampleQuotes }
    val audioPaths = listOf(
        "audio/sample1.aac",
        "audio/sample2.aac",
        "audio/sample3.aac",
        "audio/sample4.aac",
        "audio/sample5.aac",
        "audio/sample6.aac",
    )
    resetDirectory()
    val contents = mutableListOf<Content>()
    quotes.forEachIndexed { index, quote ->
        val image = images.getOrNull(index) ?: images.shuffled().first()
        val audio = audioPaths.getOrNull(index) ?: audioPaths.shuffled().first()
        contents.add(
            Content(
                imageUrl = image,
                quote = quote,
                audioPath = audio
            )
        )
    }
    contents
        .map { async { createContent(it.imageUrl, it.quote, it.audioPath) } }
        .awaitAll()
    println("created files -> ${contents.size}")
    Unit
}

suspend fun createContent(imageUrl: String, quote: String, audioPath: String) {
    val fileName = UUID.randomUUID().toString()
    val clipDurationInSeconds = 10
    val image = createQuoteImageOrNull(imageUrl = imageUrl, quote = quote)
    val imageFile = image.toJpegOrNull(nameNoExtension = fileName)
    val mp4File = image.toMp4OrNull(
        nameNoExtension = fileName,
        durationSeconds = clipDurationInSeconds
    )
    val audioFile = File(audioPath)
    val mp4withAudio = mp4File.mixAudioOrNull(
        nameNoExtension = fileName,
        audioFile = audioFile,
        durationInSeconds = clipDurationInSeconds
    )
}


fun File?.mixAudioOrNull(nameNoExtension: String, audioFile: File, durationInSeconds: Int): File? {
    val file = this ?: return null
    return with(Dispatchers.IO) {
        kotlin.runCatching {
            val videoMovie = MovieCreator.build(file.path)
            val audioTrack = AACTrackImpl(FileDataSourceImpl(audioFile))
            val clippedTrack = ClippedTrack(audioTrack, 0, durationInSeconds * 45L)
            val movie = Movie()
            videoMovie.tracks.forEach {
                when (it.handler) {
                    "soun" -> println("Adding audio track to new movie")
                    "vide" -> println("Adding video track to new movie")
                    else -> println("Adding " + it.handler + " track to new movie")
                }
                movie.addTrack(it)
            }
            movie.addTrack(clippedTrack)
            val mp4Container: Container = DefaultMp4Builder().build(movie)
            val mp4WithAudioFile = File("$DIRECTORY_WITH_AUDIO/$nameNoExtension.mp4")
            val fc = FileOutputStream(mp4WithAudioFile).channel
            mp4Container.writeContainer(fc)
            fc.close()
            mp4WithAudioFile
        }.getOrNull()
    }
}


fun ImmutableImage?.toMp4OrNull(nameNoExtension: String, durationSeconds: Int): File? {
    val img = this ?: return null
    return with(Dispatchers.IO) {
        kotlin.runCatching {
            println("Converting to video...")
            val file = File("$DIRECTORY_NO_AUDIO/$nameNoExtension.mp4")
            val channel = NIOUtils.writableFileChannel(file.path)
            try {
                val encoder = AWTSequenceEncoder(channel, Rational.R(1, durationSeconds))
                encoder.encodeImage(img.awt())
                encoder.finish()
            } finally {
                NIOUtils.closeQuietly(channel)
            }
            file
        }.getOrNull()
    }
}

suspend fun createQuoteImageOrNull(
    imageUrl: String,
    quote: String,
    frameSize: Size = Size(1080, 1920),
    fontSize: Int = 52,
): ImmutableImage? = with(Dispatchers.IO) {
    runCatching {
        println("Getting images!")
        val imageStream = getImageStreamOrNull(imageUrl) ?: return null

        println("Cropping to size!")
        val originalImage = ImmutableImage.loader().fromStream(imageStream)
        println("Original : ${originalImage.width} X ${originalImage.height} | ${originalImage.ratio()}")

        val imageCropped = originalImage.cropSize(frameSize)
        println("Cropped : ${imageCropped.width} X ${imageCropped.height} | ${imageCropped.ratio()}")

        println("Appending Quote")
        val awtImage = imageCropped.awt()
        val imageWithQuote = with(awtImage) {
            val graphics = graphics
            val font = Font(Font.MONOSPACED, Font.BOLD, fontSize)
            val fontMetrics: FontMetrics = graphics.getFontMetrics(font)

            val lines = quote.getBoundFormattedLines(frameSize.center, fontMetrics)
            val maxWidth = lines.maxOf { fontMetrics.stringWidth(it) }
            val height = fontMetrics.height * lines.size
            val quoteSize = Size(width = maxWidth, height = height)

            var yLoc = (frameSize.center.height - quoteSize.center.height) + fontMetrics.ascent
            val xLoc = frameSize.center.width - quoteSize.center.width

            lines.forEach { line ->
                val lineWidth = fontMetrics.stringWidth(line)
                val attributedText = AttributedString(line)
                attributedText.addAttribute(TextAttribute.FONT, font)
                attributedText.addAttribute(TextAttribute.FOREGROUND, Color.WHITE)
                graphics.color = Color.BLACK
                graphics.fill3DRect(
                    xLoc + (fontMetrics.ascent * 0.25).roundToInt(),
                    yLoc - fontMetrics.ascent,
                    lineWidth,
                    fontMetrics.height,
                    true
                )
                graphics.color = Color.WHITE
                graphics.drawRoundRect(
                    xLoc + (fontMetrics.ascent * 0.25).roundToInt(),
                    yLoc - fontMetrics.ascent,
                    lineWidth,
                    fontMetrics.height,
                    8,
                    8
                )
                graphics.drawString(attributedText.iterator, xLoc, yLoc)
                yLoc += fontMetrics.height
            }

            ImmutableImage.fromAwt(this)
        }
        return imageWithQuote
    }.getOrNull()
}


data class Size(val width: Int, val height: Int) {
    val center get() = this.copy(width = width / 2, height = height / 2)
}


fun ImmutableImage.cropSize(size: Size): ImmutableImage {
    return cover(size.width, size.height)
}

fun resetDirectory() {
    println("Clean up!")
    val parentDirectory = File(DIRECTORY_OUTPUT)
    parentDirectory.deleteRecursively()
    val imageDirectory = File(DIRECTORY_IMAGES)
    val noAudiDirectory = File(DIRECTORY_NO_AUDIO)
    val audiDirectory = File(DIRECTORY_WITH_AUDIO)
    parentDirectory.mkdir()
    imageDirectory.mkdir()
    noAudiDirectory.mkdir()
    audiDirectory.mkdir()
}

fun getImageStreamOrNull(imageUrl: String): InputStream? {
    val okHttpClient = OkHttpClient()
    val imageRequest = Request.Builder().url(imageUrl).build()
    val response = okHttpClient.newCall(imageRequest).execute()
    return response.body?.byteStream()
}

fun ImmutableImage?.toJpegOrNull(nameNoExtension: String): File? {
    val img = this ?: return null
    return with(Dispatchers.IO) {
        kotlin.runCatching {
            val writer = JpegWriter().withCompression(50)
            val file = img.output(writer, File("$DIRECTORY_OUTPUT/images/$nameNoExtension.jpeg"))
            println("Created : ${file?.absoluteFile}")
            file
        }.getOrNull()
    }
}

fun String.getBoundFormattedLines(bounds: Size, fontMetrics: FontMetrics): List<String> {
    val input = this
    val words = input.split(" ")
    val windowWidth = bounds.width
    var lineStart = ""
    val lines = mutableListOf<String>()
    words.forEachIndexed { index, word ->
        lineStart = "$lineStart $word"
        val widthOnWindow = fontMetrics.stringWidth(lineStart)
        val isFittingOnWindow = widthOnWindow < windowWidth
        if (!isFittingOnWindow) {
            lines.add(lineStart)
            lineStart = ""
        }
    }
    if (lineStart.isNotEmpty()) {
        lines.add(lineStart)
    }
    return lines
}

