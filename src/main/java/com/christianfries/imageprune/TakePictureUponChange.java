package com.christianfries.imageprune;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import javax.imageio.ImageIO;

public class TakePictureUponChange {

	private static final ExecutorService executorFileTransfer = Executors.newSingleThreadExecutor();
	private static final ExecutorService executorImageCompare = Executors.newSingleThreadExecutor();

	public static void main(String[] args) throws IOException, InterruptedException {

		Double threshold = Double.valueOf(args[0]);
		final String	filenamePrefix = args[1];
		final String	targetDir = args[2];
		final String	imageCommand = args[3];

		ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash");
		processBuilder.directory(null);
		processBuilder.redirectError(new File(TakePictureUponChange.class.getSimpleName() + ".log"));
		Process process = processBuilder.start();

		OutputStream stdin = process.getOutputStream();
		InputStream stdout = process.getInputStream();

		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));
		BufferedReader reader = new BufferedReader (new InputStreamReader(stdout));

		// Launch image script, will save image to filename and write filename to reader.
		String script = ""
				+ "for(( ; ; ))\n"
				+ "do\n"
				+ "  timestamp=$(date +%s.%3N)\n"
				+ "  filename=" + filenamePrefix + "-$timestamp.jpg\n"
				+ "  " + imageCommand.replace("{filename}", "$filename") + "\n"
				+ "  echo $filename\n"
				+ "done\n";
		writer.write(script);
		writer.flush();

		System.out.println(script);

		BufferedImage reference = null;

		while(true) {
			try {
				String filename = reader.readLine();
				System.out.println(filename);
				// Load the image
				long timeReadStart = System.currentTimeMillis();
				final BufferedImage image = ImageIO.read(new File(filename));
				long timeReadEnd = System.currentTimeMillis();
				System.out.println("Read....: " + ((timeReadEnd-timeReadStart)/1000.0));

				final BufferedImage referenceImage = reference;
				executorImageCompare.submit(() -> saveWhenDifferent(referenceImage, image, threshold, filename, targetDir));
				reference = image;
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static boolean saveWhenDifferent(final BufferedImage reference, final BufferedImage image, double threshold, String filename, String targetDir) throws IOException {
		long timeCompareStart = System.currentTimeMillis();
		double level = getImageDifference(reference, image, true);
		long timeCompareEnd = System.currentTimeMillis();
		System.out.println("Compare.: " + ((timeCompareEnd-timeCompareStart)/1000.0));

		final boolean isDifferent = level > threshold;

		executorFileTransfer.submit(() -> {
			try {
				long timeCleanStart = System.currentTimeMillis();
				if(level > threshold) {
					String target = targetDir + File.separator + filename;
					Files.copy(Paths.get(filename), Paths.get(target));
					Files.delete(Paths.get(filename));
					System.out.println(filename + "\t" + level + "\ttransfered.");
				}
				else {
					Files.delete(Paths.get(filename));
					System.out.println(filename + "\t" + level + "\tdeleted.");
				}
				long timeCleanEnd = System.currentTimeMillis();
				System.out.println("Clean.: " + ((timeCleanEnd-timeCleanStart)/1000.0));
			}
			catch(Exception e)
			{
				System.out.println(filename + "\t" + level + "\tFAILED.");
				e.printStackTrace();
			}
		});

		return isDifferent;
	}

	private static double getImageDifference(final BufferedImage reference, final BufferedImage image, boolean blackWhite) throws IOException {

		if(reference == null) return Double.MAX_VALUE;

		boolean isImageHasAlpha = reference.getAlphaRaster() != null;
		System.out.println(isImageHasAlpha);

		byte[] pixelsReference = ((DataBufferByte) reference.getRaster().getDataBuffer()).getData();
		byte[] pixelsImage = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

		double meanReference = getImageMean(pixelsReference);
		double meanImage = getImageMean(pixelsImage);

		//		double varReference = getImageVar(pixelsReference);

		double covarSum = 0;
		double varSumReference = 0;
		double varSumImage = 0;

		for(int i=0; i < pixelsReference.length/3; i++) {

			int red1 = Byte.toUnsignedInt(pixelsReference[3*i+0]);
			int green1 = Byte.toUnsignedInt(pixelsReference[3*i+1]);
			int blue1 = Byte.toUnsignedInt(pixelsReference[3*i+2]);

			int red2 = Byte.toUnsignedInt(pixelsImage[3*i+0]);
			int green2 = Byte.toUnsignedInt(pixelsImage[3*i+1]);
			int blue2 = Byte.toUnsignedInt(pixelsImage[3*i+2]);

			double diff1 = (double)(red1+green1+blue1)/(3.0*255.0)-meanReference;
			double diff2 = (double)(red2+green2+blue2)/(3.0*255.0)-meanImage;

			covarSum += diff1*diff2;
			varSumReference += diff1*diff1;
			varSumImage += diff2*diff2;
		}

		double level = covarSum / Math.sqrt(varSumReference*varSumImage);

		return (1.0 - level) / 2.0;
	}

	private static double getImageMean(final byte[] pixels) {
		return IntStream.range(0, pixels.length).parallel().mapToDouble(i -> (double)Byte.toUnsignedInt(pixels[i]) / 255.0).average().orElse(Double.NaN);
	}

	private static double getImageVar(final byte[] pixels, double mean) {
		return IntStream.range(0, pixels.length).parallel().mapToDouble(i -> Math.pow((double)Byte.toUnsignedInt(pixels[i]) / 255.0 - mean,2)).average().orElse(Double.NaN);
	}

	private static double getImageSigma(BufferedImage image, double mean) {
		return Math.sqrt(LongStream.range(0, image.getHeight()).parallel().mapToDouble(i -> {
			double sumOfSquaresForRow = 0;
			int y = (int)i;
			for (int x = 0; x < image.getWidth(); x++) {
				//Retrieving contents of a pixel
				int pixel = image.getRGB(x,y);
				//Creating a Color object from pixel value
				Color color = new Color(pixel, true);
				//Retrieving the R G B values
				int red = color.getRed();
				int green = color.getGreen();
				int blue = color.getBlue();

				sumOfSquaresForRow += Math.pow((red+green+blue)/255.0/3.0 - mean,2);
			}
			return sumOfSquaresForRow;
		}).sum() / (image.getHeight() * image.getWidth()));
	}

	private static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) throws IOException {
		Image resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_FAST);
		BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
		return outputImage;
	}
}
