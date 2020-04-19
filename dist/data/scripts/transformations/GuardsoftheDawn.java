package transformations;

import l2server.gameserver.datatables.SkillTable;
import l2server.gameserver.instancemanager.TransformationManager;
import l2server.gameserver.model.L2Transformation;

public class GuardsoftheDawn extends L2Transformation
{
	private static final int[] SKILLS = {5491, 619, 963};

	public GuardsoftheDawn()
	{
		// id, colRadius, colHeight
		super(113, 8, 23.5);
	}

	@Override
	public void onTransform()
	{
		if (getPlayer().getTransformationId() != 113 || getPlayer().isCursedWeaponEquipped())
		{
			return;
		}

		transformedSkills();
	}

	public void transformedSkills()
	{
		// Decrease Bow/Crossbow Attack Speed
		getPlayer().addSkill(SkillTable.getInstance().getInfo(5491, 1), false);
		// Transform Dispel
		getPlayer().addSkill(SkillTable.getInstance().getInfo(619, 1), false);
		// Guard Ambush
		getPlayer().addSkill(SkillTable.getInstance().getInfo(963, 1), false);

		getPlayer().setTransformAllowedSkills(SKILLS);
	}

	@Override
	public void onUntransform()
	{
		removeSkills();
	}

	public void removeSkills()
	{
		// Decrease Bow/Crossbow Attack Speed
		getPlayer().removeSkill(SkillTable.getInstance().getInfo(5491, 1), false);
		// Transform Dispel
		getPlayer().removeSkill(SkillTable.getInstance().getInfo(619, 1), false);
		// Guard's Ambush
		getPlayer().removeSkill(SkillTable.getInstance().getInfo(963, 1), false);

		getPlayer().setTransformAllowedSkills(EMPTY_ARRAY);
	}

	public static void main(String[] args)
	{
		TransformationManager.getInstance().registerTransformation(new GuardsoftheDawn());
	}
}
