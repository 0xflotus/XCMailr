package etc;

import java.io.IOException;

import javax.activation.DataSource;

public class AttachmentEntry
{
    public AttachmentEntry(DataSource attachment) throws IOException
    {
        this.name = attachment.getName();
        this.contentType = attachment.getContentType();
        this.size = attachment.getInputStream().available();
    }

    public String name;

    public String contentType;

    public int size;
}
