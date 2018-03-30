package gallerymine.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import gallerymine.model.support.ActionKind;
import lombok.Data;

import javax.xml.bind.annotation.XmlTransient;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by sergii_puliaiev on 6/22/17.
 */
@Data
public class ActionRequest {

    private ActionKind kind;

    List<String> operands = new ArrayList<>();

    @XmlTransient
    @JsonIgnore
    public String getFirstOperand() {
        return (operands != null && operands.size() > 0) ? operands.get(0) : null;
    }
}
