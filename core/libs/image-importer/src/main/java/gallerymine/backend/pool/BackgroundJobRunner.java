package gallerymine.backend.pool;

public abstract class BackgroundJobRunner implements Runnable {

    private String name = null;

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
