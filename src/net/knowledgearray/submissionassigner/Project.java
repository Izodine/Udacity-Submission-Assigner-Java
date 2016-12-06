package net.knowledgearray.submissionassigner;

/**
 * Created by Anthony M. Santiago on 10/26/2016.
 */
public final class Project {
    public int project_id;
    public String language;

    public Project(int project_id, String language){
        this.project_id = project_id;
        this.language = language;
    }
}
