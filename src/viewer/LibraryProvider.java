package viewer;

import java.io.File;
import java.util.Map;
import java.util.Set;

public interface LibraryProvider {
    int getRating(File f);
    void setRating(File f, int r);
    void saveRatings();
    Set<String> getFolderTags(File f);
    void setFolderTags(File f, Set<String> tags);
    Set<String> getAllTags();
    Map<String, Collection> getCollections();
    void saveCollections();
    void refreshDisplay();
}
