package org.example

import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.path
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.Path

fun splitAndIndex() {
    val inputImagePath = "src/main/resources/stretchrmain.png"
    val outputFolderPath = "src/main/resources/tiles"

    val originalImage: BufferedImage = ImageIO.read(File(inputImagePath))

    val rows = 4
    val cols = 4
    val subImageWidth = originalImage.width / cols
    val subImageHeight = originalImage.height / rows

    val outputFolder = File(outputFolderPath)
    if (!outputFolder.exists()) {
        outputFolder.mkdirs()
    }

    var count = 0
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val subImage: BufferedImage = originalImage.getSubimage(
                col * subImageWidth,
                row * subImageHeight,
                subImageWidth,
                subImageHeight
            )
            val outputFilePath = "$outputFolderPath/i$count.png"
            ImageIO.write(subImage, "png", File(outputFilePath))
            println("Saved: $outputFilePath")
            count++
        }
    }
    println("Image splitting completed.")
}

fun encipher(text: String, outputImageName: String, outputImagePath: Path): String {
    val inputFolderPath = "tiles"
    val tileSize = 32

    fun getTilePositions(index: Int, squareSize: Int, tileSize: Int): Pair<Int, Int> {
        return (index % squareSize) * tileSize to (index / squareSize) * tileSize
    }

    fun splitByteToNibbles(byte: Byte): Pair<Byte, Byte> {
        val highNibble = ((byte.toInt() shr 4) and 0x0F).toByte()
        val lowNibble = (byte.toInt() and 0x0F).toByte()
        return Pair(highNibble, lowNibble)
    }

    val tileList = text.toByteArray().flatMap { byte ->
        val (highNibble, lowNibble) = splitByteToNibbles(byte)
        listOf(highNibble, lowNibble)
    }

    val numTiles = tileList.size * 2
    val squareSize = kotlin.math.ceil(kotlin.math.sqrt(numTiles.toDouble())).toInt()
    val outputImageWidth = tileSize * squareSize
    val outputImageHeight = tileSize * squareSize
    val outputImage = BufferedImage(outputImageWidth, outputImageHeight, BufferedImage.TYPE_INT_ARGB)

    val graphics = outputImage.createGraphics()

    for ((index, tile) in tileList.withIndex()) {
        val tileImagePath = "$inputFolderPath/i$tile.png"
        val tileImageStream = MyClass::class.java.classLoader.getResourceAsStream(tileImagePath)
            ?: throw IllegalArgumentException("Tile image not found: $tileImagePath")
        val tileImage = ImageIO.read(tileImageStream)

        val (x, y) = getTilePositions(index, squareSize, tileSize)
        graphics.drawImage(tileImage, x, y, null)
    }

    graphics.dispose()

    ImageIO.write(outputImage, "png", File("$outputImagePath/$outputImageName.png"))
    return ("Saved in $outputImagePath/$outputImageName.png")
}

fun decipher(inputImageFile: File): String {
    val tileSize = 32
    val tileHighestIndex = 15

    fun compareImages(imgA: BufferedImage, imgB: BufferedImage): Boolean {
        if (imgA.width != imgB.width || imgA.height != imgB.height) return false
        for (y in 0 until imgA.height) {
            for (x in 0 until imgA.width) {
                if (imgA.getRGB(x, y) != imgB.getRGB(x, y)) return false
            }
        }
        return true
    }

    fun getTilePositions(index: Int, squareSize: Int, tileSize: Int) : Pair<Int, Int> {
        return (index % squareSize) * tileSize to (index / squareSize) * tileSize
    }

    fun combineNibblesToByte(highNibble: Byte, lowNibble: Byte): Byte {
        return ((highNibble.toInt() shl 4) or (lowNibble.toInt() and 0x0F)).toByte()
    }

    val encryptedImage = ImageIO.read(inputImageFile)
    val squareSize = encryptedImage.width / tileSize

    val tileList = mutableListOf<Byte>()

    for (index in 0 until squareSize * squareSize) {
        val (x, y) = getTilePositions(index, squareSize, tileSize)
        val tileImage = encryptedImage.getSubimage(x, y, tileSize, tileSize)

        for (i in 0..tileHighestIndex) {
            val tileImagePath = "tiles/i$i.png"
            val tileImageStream = MyClass::class.java.classLoader.getResourceAsStream(tileImagePath)
                ?: throw IllegalArgumentException("Tile image not found: $tileImagePath")
            val expectedTileImage = ImageIO.read(tileImageStream)
            if (compareImages(tileImage, expectedTileImage)) {
                tileList.add(i.toByte())
                break
            }
        }
    }

    val byteArray = tileList.chunked(2)
        .map { (highNibble, lowNibble) -> combineNibblesToByte(highNibble, lowNibble) }
        .toByteArray()

    return byteArray.toString(Charsets.UTF_8)
}

class Stretchr: CliktCommand(name="stretchr") {
    init {
        completionOption()
    }

    override fun run() = Unit
}

class Encipher: CliktCommand(help="Encipher a text") {
    init {
        completionOption()
    }

    private val word by argument().help("The word to encipher")
    private val imageName by argument().help("The name of the image")
    private val path by argument().path().help("The path where the enciphered image will be found").default(Path("."))

    override fun run() {
        echo(encipher(word, imageName, path))
    }
}

class Decipher: CliktCommand(help="Decipher an image") {
    init {
        completionOption()
    }

    private val fileName by argument().file().help("The name of the image")

    override fun run() {
        echo(decipher(fileName))
    }
}

fun main(args: Array<String>) = Stretchr()
    .subcommands(Encipher(), Decipher())
    .main(args)

class MyClass