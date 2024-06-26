package exoticatechnologies.cargo;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import lombok.Getter;

import java.util.Objects;

public class CrateSpecialData extends SpecialItemData {
    private final CargoAPI cargo;

    public CrateSpecialData() {
        super("et_crate", "");
        this.cargo = Global.getFactory().createCargo(true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        CrateSpecialData that = (CrateSpecialData) o;

        return Objects.equals(cargo, that.cargo);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (cargo != null ? cargo.hashCode() : 0);
        return result;
    }

    public CargoAPI getCargo() { return cargo; }
}
