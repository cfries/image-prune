package com.christianfries.imageprune;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import java.util.stream.LongStream;

import javax.imageio.ImageIO;

public class TakePictureUponChange {

	public static void main(String[] args) throws IOException, InterruptedException {

		final String	fileName = args[0];
		final String	targetDir = args[1];
		final String[]	imageCommand = Arrays.copyOfRange(args, 1, args.length);

		Double threshold = Double.valueOf(args[0]);

		BufferedImage reference = null;

		while(true) {
			try {
				ProcessBuilder processBuilder = new ProcessBuilder(imageCommand);
				processBuilder.directory(null);
				File log = new File("TakePictureUponChange.log");
				processBuilder.redirectErrorStream(true);
				processBuilder.redirectOutput(Redirect.appendTo(log));
				Process process = processBuilder.start();
				assert processBuilder.redirectInput() == Redirect.PIPE;
				assert processBuilder.redirectOutput().file() == log;
				assert process.getInputStream().read() == -1;
				process.waitFor();

				// Load the image
				BufferedImage image = ImageIO.read(new File(fileName));

				double level = getImageDifference(reference, image, true);
				reference = image;

				if(level > threshold) {
					String target = targetDir + File.separator + fileName + "-" + System.currentTimeMillis();
					Files.copy(Paths.get(fileName), Paths.get(target));
					System.out.println("Transfered image to ");
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static double getImageDifference(final BufferedImage reference, final BufferedImage image, boolean blackWhite) throws IOException {

		if(reference == null) return Double.MAX_VALUE;

		int width = reference.getWidth() / 3;
		int height = reference.getHeight() / 3;

		final BufferedImage referenceScaled = resizeImage(reference, width, height);
		final BufferedImage imageScaled = resizeImage(image, width, height);

		double meanReference = getImageMean(referenceScaled);
		double meanImage = getImageMean(imageScaled);

		double sigmaReference = getImageSigma(referenceScaled, meanReference);
		double sigmaImage = getImageSigma(imageScaled, meanImage);


		double difference = LongStream.range(0, referenceScaled.getHeight()).parallel().mapToDouble(i -> {
			double differenceForRow = 0;
			int y = (int)i;
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

				if(blackWhite) {
					double differenceOnPixel = ((double)(red1+green1+blue1)/(3.0*255.0)-meanReference) * ((double)(red2+green2+blue2)/(3.0*255.0)-meanImage);
					//					double differenceOnPixel = Math.abs((double)(red1+green1+blue1)/(3.0*255.0) - meanReference/meanImage * (double)(red2+green2+blue2)/(3.0*255.0));
					differenceForRow += differenceOnPixel;
				}
				else {
					differenceForRow += Math.abs(red1-red2) + Math.abs(green1-green2) + Math.abs(blue1-blue2);
				}
			}
			return differenceForRow;
		}).sum();

		double level = difference / (referenceScaled.getWidth()*referenceScaled.getHeight());

		return (1.0 - level / sigmaImage / sigmaReference) / 2.0;
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
