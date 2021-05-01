package com.christianfries.surveillancecamera;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

public class TakePictureUponChange {

	private static final ExecutorService executorFileTransfer = Executors.newSingleThreadExecutor();
	private static final ExecutorService executorImageCompare = Executors.newSingleThreadExecutor();

	public static void main(String[] args) throws IOException, InterruptedException {

		final Double threshold = Double.valueOf(args[0]);
		final String	filenamePrefix = args[1];
		final String	targetDir = args[2];
		final String	imageCommand = args[3];

		final ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash");
		processBuilder.directory(null);
		processBuilder.redirectError(new File(TakePictureUponChange.class.getSimpleName() + ".log"));
		final Process process = processBuilder.start();

		final OutputStream stdin = process.getOutputStream();
		final InputStream stdout = process.getInputStream();

		final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));
		final BufferedReader reader = new BufferedReader (new InputStreamReader(stdout));

		// Launch image script, will save image to filename and write filename to reader.
		final String script = ""
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
				final String filename = reader.readLine();
				// Load the image
				final long timeReadStart = System.currentTimeMillis();
				final BufferedImage image = ImageIO.read(new File(filename));
				final long timeReadEnd = System.currentTimeMillis();
				//				System.out.println("Read....: " + ((timeReadEnd-timeReadStart)/1000.0));

				final BufferedImage referenceImage = reference;
				executorImageCompare.submit(() -> saveWhenDifferent(referenceImage, image, threshold, filename, targetDir));
				reference = image;
			}
			catch(final Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static boolean saveWhenDifferent(final BufferedImage reference, final BufferedImage image, double threshold, String filename, String targetDir) throws IOException {
		final long timeCompareStart = System.currentTimeMillis();
		final double level = new ImageCompare().getImageDifference(reference, image);
		final long timeCompareEnd = System.currentTimeMillis();

		final double timeCompare = ((timeCompareEnd-timeCompareStart)/1000.0);
		final boolean isDifferent = level > threshold;

		executorFileTransfer.submit(() -> {
			try {
				final long timeCleanStart = System.currentTimeMillis();
				if(level > threshold) {
					final String target = targetDir + File.separator + filename;
					Files.copy(Paths.get(filename), Paths.get(target));
					Files.delete(Paths.get(filename));
				}
				else {
					Files.delete(Paths.get(filename));
				}
				final long timeCleanEnd = System.currentTimeMillis();
				final double timeTransfer = (timeCleanEnd-timeCleanStart)/1000.0;

				System.out.println(
						filename + "\t" + String.format("%8.5f", level) + "\t" +
								"\t" + String.format("%-10s (compare: %5.3f s, transfer: %5.3f s).", ((level > threshold) ? "transfered" : "deleted"), timeCompare, timeTransfer));
			}
			catch(final Exception e)
			{
				System.out.println(filename + "\t" + level + "\tFAILED.");
				e.printStackTrace();
			}
		});

		return isDifferent;
	}

}
