package com.christianfries.imageprune;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
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
import java.util.stream.LongStream;

import javax.imageio.ImageIO;

public class TakePictureUponChange {

	public static void main(String[] args) throws IOException, InterruptedException {

		Double threshold = Double.valueOf(args[0]);
		final String	fileName = args[1];
		final String	targetDir = args[2];
		final String	imageCommand = args[3];

		ExecutorService executorService = Executors.newFixedThreadPool(4);

		ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash");
		processBuilder.directory(null);
		processBuilder.redirectError(new File(TakePictureUponChange.class.getSimpleName() + ".log"));
		Process process = processBuilder.start();

		OutputStream stdin = process.getOutputStream();
		InputStream stderr = process.getErrorStream();
		InputStream stdout = process.getInputStream();

		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));
		BufferedReader reader = new BufferedReader (new InputStreamReader(stdout));

		String script = ""
				+ "for(( ; ; ))\n"
				+ "do\n"
				+ "  timestamp=$(date +%s)\n"
				+ "  filename=image-$timestamp.jpg\n"
				+ "  " + imageCommand + "\n"
				+ "  echo $filename\n"
				+ "done\n";
		writer.write(script);
		writer.flush();


		BufferedImage reference = null;

		while(true) {

			try {
				String filename = reader.readLine();
				System.out.println(filename);
				// Load the image
				final BufferedImage image = ImageIO.read(new File(filename));
				final BufferedImage referenceImage = reference;
				executorService.submit(() -> saveWhenDifferent(referenceImage, image, threshold, filename, targetDir));
				reference = image;
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static void saveWhenDifferent(final BufferedImage reference, final BufferedImage image, double threshold, String filename, String targetDir) {
		try {
			double level = getImageDifference(reference, image, true);

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
		}
		catch(Exception e)
		{

		}
	}

	private static double getImageDifference(final BufferedImage reference, final BufferedImage image, boolean blackWhite) throws IOException {

		if(reference == null) return Double.MAX_VALUE;

		int width = reference.getWidth() / 6;
		int height = reference.getHeight() / 6;

		final BufferedImage referenceScaled = resizeImage(reference, width, height);
		final BufferedImage imageScaled = resizeImage(image, width, height);

		double meanReference = getImageMean(referenceScaled);
		double meanImage = getImageMean(imageScaled);

		double covarSum = 0;
		double varSumReference = 0;
		double varSumImage = 0;
		for(int y =0; y < referenceScaled.getHeight(); y++) {
			for (int x = 0; x < referenceScaled.getWidth(); x++) {
				//Retrieving contents of a pixel
				int pixel1 = referenceScaled.getRGB(x,y);
				int pixel2 = imageScaled.getRGB(x,y);
				//Creating a Color object from pixel value
				Color color1 = new Color(pixel1, true);
				//Retrieving the R G B values
				int red1 = color1.getRed();
				int green1 = color1.getGreen();
				int blue1 = color1.getBlue();
				Color color2 = new Color(pixel2, true);
				//Retrieving the R G B values
				int red2 = color2.getRed();
				int green2 = color2.getGreen();
				int blue2 = color2.getBlue();

				double diff1 = (double)(red1+green1+blue1)/(3.0*255.0)-meanReference;
				double diff2 = (double)(red2+green2+blue2)/(3.0*255.0)-meanImage;

				covarSum += diff1*diff2;
				varSumReference += diff1*diff1;
				varSumImage += diff2*diff2;
			}
		}

		double level = covarSum / Math.sqrt(varSumReference*varSumImage);

		return (1.0 - level) / 2.0;
	}

	private static double getImageMean(BufferedImage image) {
		return LongStream.range(0, image.getHeight()).parallel().mapToDouble(i -> {
			double sumForRow = 0;
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

				sumForRow += (double)(red+green+blue) / 255.0 / 3.0;
			}
			return sumForRow;
		}).sum() / (image.getHeight() * image.getWidth());
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
		Image resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
		BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
		return outputImage;
	}
}
