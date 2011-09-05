package nl.javadude.scannit.reader;

import com.google.common.base.Predicate;
import de.schlichtherle.truezip.file.TFile;
import de.schlichtherle.truezip.fs.FsSyncException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.ServiceConfigurationError;

import static com.google.common.collect.Lists.newArrayList;

public class ArchiveEntrySupplier {
	private final URI uri;

	public ArchiveEntrySupplier(URI uri) {
		this.uri = uri;
	}

	public void withArchiveEntries(Predicate<TFile> with) {
		TFile tFile = getTFile(uri);
		List<TFile> entries = list(tFile);

		for (TFile file : entries) {
			with.apply(file);
		}

		closeTFile(tFile);
	}

	private List<TFile> list(TFile tFile) {
		List<TFile> entries = newArrayList();
		if (tFile.isArchive()) {
			gatherEntries(tFile, entries, true);
		} else {
			gatherEntries(tFile, entries, false);
		}
		return entries;
	}

	protected void closeTFile(TFile tFile) {
		try {
			if (tFile.isArchive()) {
				TFile.umount(tFile);
			}
		} catch (FsSyncException e) {
			logger.error("Could not umount {}, continuing", tFile);
			logger.debug("Exception was: ", e);
		}
	}

	private TFile getTFile(URI uri) {
		if (uri.getPath() != null) {
			// if the URI has a path, this means it is a real file, use the detection of the path, and not look at the scheme
			return new TFile(uri.getPath());
		} else {
			return new TFile(uri);
		}
	}

	private void gatherEntries(TFile tFile, List<TFile> files, boolean scanInArchives) {
		try {
			boolean isArchive = tFile.isArchive();
			boolean isNormalDir = !isArchive && tFile.isDirectory();
			boolean hasFiles = (isArchive || isNormalDir) && tFile.listFiles() != null;

			if ((isNormalDir && hasFiles) || isArchive && scanInArchives && hasFiles) {
				logger.trace("Listing directory/archive of file: {}", tFile);
				for (TFile file : tFile.listFiles()) {
					gatherEntries(file, files, false);
				}
			} else if (tFile.isFile() || tFile.isEntry()) {
				logger.trace("Found file/entry {}", tFile);
				files.add(tFile);
			}
		} catch (RuntimeException re) {
			logger.error("Error scanning {}, continuing...", tFile);
			logger.debug("Exception was: ", re);
		} catch (ServiceConfigurationError re) {
			logger.error("Error scanning {}, continuing...", tFile);
			logger.debug("Error was: ", re);
		}
	}

	private static final Logger logger = LoggerFactory.getLogger(ArchiveEntrySupplier.class);
}