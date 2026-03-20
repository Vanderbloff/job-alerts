package exception;

public class SlugNotFoundException extends RuntimeException {

    private final String slug;

    public SlugNotFoundException(String slug) {
        super("Slug not found: " + slug);
        this.slug = slug;
    }

    public String getSlug() {
        return slug;
    }
}