package rat.poison.scripts.esp

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils.clamp
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Align
import com.sun.jna.Memory
import rat.poison.curSettings
import rat.poison.game.*
import rat.poison.game.CSGO.csgoEXE
import rat.poison.game.entity.*
import rat.poison.game.forEntities
import rat.poison.game.netvars.NetVarOffsets.iClip1
import rat.poison.game.netvars.NetVarOffsets.iPrimaryReserveAmmoCount
import rat.poison.game.netvars.NetVarOffsets.m_Collision
import rat.poison.game.netvars.NetVarOffsets.rgflCoordinateFrame
import rat.poison.overlay.App
import rat.poison.settings.DANGER_ZONE
import rat.poison.settings.HEAD_BONE
import rat.poison.settings.MENUTOG
import rat.poison.toLocale
import rat.poison.utils.Vector
import rat.poison.utils.generalUtil.strToBool
import rat.poison.utils.generalUtil.strToColor
import rat.poison.utils.generalUtil.strToColorGDX
import rat.poison.utils.notInGame
import kotlin.math.abs
import kotlin.math.sign

data class BoundingBox(var left: Float = -1F, var right: Float = -1F, var top: Float = -1F, var bottom: Float = -1F)


//Combine left/right top/bottom ooga booga
fun boxEsp() = App {
	if ((!curSettings["ENABLE_BOX_ESP"].strToBool() && !curSettings["BOX_ESP_DETAILS"].strToBool()) || !curSettings["ENABLE_ESP"].strToBool() || MENUTOG || notInGame) return@App

	val advancedBBox = curSettings["ADVANCED_BOUNDING_BOX"].strToBool()

	val drawBox = curSettings["ENABLE_BOX_ESP"].strToBool()
	val drawBoxDetails = curSettings["BOX_ESP_DETAILS"].strToBool()

	val bEspName = curSettings["BOX_ESP_NAME"].strToBool(); 		val bEspNamePos = curSettings["BOX_ESP_NAME_POS"].replace("\"", "")
	val bEspWeapon = curSettings["BOX_ESP_WEAPON"].strToBool(); 	val bEspWeaponPos = curSettings["BOX_ESP_WEAPON_POS"].replace("\"", "")
	val bEspHealth = curSettings["BOX_ESP_HEALTH"].strToBool(); 	val bEspHealthPos = curSettings["BOX_ESP_HEALTH_POS"].replace("\"", "")
	val bEspArmor = curSettings["BOX_ESP_ARMOR"].strToBool(); 		val bEspArmorPos = curSettings["BOX_ESP_ARMOR_POS"].replace("\"", "")
	val bEspAmmo = curSettings["BOX_ESP_AMMO"].strToBool(); 		val bEspAmmoPos = curSettings["BOX_ESP_AMMO_POS"].replace("\"", "")
	val bEspHelmet = curSettings["BOX_ESP_HELMET"].strToBool(); 	val bEspHelmetPos = curSettings["BOX_ESP_HELMET_POS"].replace("\"", "")
	val bEspKevlar = curSettings["BOX_ESP_KEVLAR"].strToBool(); 	val bEspKevlarPos = curSettings["BOX_ESP_KEVLAR_POS"].replace("\"", "")
	val bEspScoped = curSettings["BOX_ESP_SCOPED"].strToBool(); 	val bEspScopedPos = curSettings["BOX_ESP_SCOPED_POS"].replace("\"", "")
	val bEspFlashed = curSettings["BOX_ESP_FLASHED"].strToBool();	val bEspFlashedPos = curSettings["BOX_ESP_FLASHED_POS"].replace("\"", "")

	val showTeam = curSettings["BOX_SHOW_TEAM"].strToBool()
	val showEnemy = curSettings["BOX_SHOW_ENEMIES"].strToBool()
	val showWeapons = curSettings["BOX_SHOW_WEAPONS"].strToBool()
	val showDefuseKits = curSettings["BOX_SHOW_DEFUSERS"].strToBool()

	forEntities { //Player & Weapon boxes
		val ent = it.entity
		val isPlayer = it.type == EntityType.CCSPlayer

		if (isPlayer || it.type.weapon) {
			//Return if not onscreen
			if (!worldToScreen(ent.position(), Vector())) return@forEntities

			//Team + Dormant + Dead + Self check
			var health = 0
			if (isPlayer) { health = ent.health() }
			val onTeam = ent.team() == me.team()
			if (isPlayer && !DANGER_ZONE && (ent == me || ent.dormant() || ent.dead() || (!showEnemy && !onTeam) || (!showTeam && onTeam))) return@forEntities
			if (!isPlayer && !showWeapons) return@forEntities

			val bbox: BoundingBox = if ((isPlayer && advancedBBox) || (!isPlayer && showWeapons)) {
				setupAccurateBox(ent)
			} else {
				setupFakeBox(ent)
			}

			if (bbox.left == -1F && bbox.right == -1F) return@forEntities //Invalid

			val boxWidth = bbox.right - bbox.left
			val boxHeight = bbox.bottom - bbox.top
			val barWidth = clamp(boxWidth * .025F, 2F, 20F)

			shapeRenderer.begin()
			shapeRenderer.set(ShapeRenderer.ShapeType.Line)

			//Draw the entity bounding box
			if (drawBox) {
				if (isPlayer) {
					when {
						curSettings["BOX_SHOW_HEALTH"].strToBool() -> {
							shapeRenderer.setColor((255 - 2.55F * health) / 255F, (2.55F * health) / 255F, 0F, 1F)
						}

						onTeam -> {
							shapeRenderer.color = curSettings["BOX_TEAM_COLOR"].strToColorGDX()
						}

						else -> {
							shapeRenderer.color = curSettings["BOX_ENEMY_COLOR"].strToColorGDX()
						}
					}
				} else {
					shapeRenderer.color = curSettings["BOX_WEAPON_COLOR"].strToColorGDX()
				}

				shapeRenderer.rect(bbox.left, bbox.top, boxWidth, boxHeight)
			}

			if (!drawBoxDetails) return@forEntities

			//Setup entity values
			val entityMemory = csgoEXE.read(ent, 45948)!!

			//Set filled for bars
			shapeRenderer.set(ShapeRenderer.ShapeType.Filled)

			//Draw possible left elements
			var leftShift = 2
			val boxDetailsLeftText = StringBuilder()
			boxDetailsLeftText.append("")

			if (bEspHealth && bEspHealthPos == "LEFT" && isPlayer) {
				shapeRenderer.color = Color.BLACK
				shapeRenderer.rect(bbox.left - (barWidth * leftShift), bbox.top, barWidth, boxHeight)

				shapeRenderer.color = Color(1F - (.01F * health), (.01F * health), 0F, 1F)
				shapeRenderer.rect(bbox.left - (barWidth * leftShift), bbox.bottom, barWidth, -(boxHeight * (health/100F)))

				leftShift += 2
			}

			if (bEspArmor && bEspArmorPos == "LEFT" && isPlayer) {
				shapeRenderer.color = Color.BLACK
				shapeRenderer.rect(bbox.left - (barWidth * leftShift), bbox.top, barWidth, boxHeight)

				shapeRenderer.color = Color(0F, .3F, 1F, 1F)
				shapeRenderer.rect(bbox.left - (barWidth * leftShift), bbox.bottom, barWidth, -(boxHeight * (entityMemory.armor()/100F)))

				leftShift += 2
			}

			if (bEspHelmet && bEspHelmetPos == "LEFT" && isPlayer) {
				boxDetailsLeftText.append(if (entityMemory.hasHelmet()) "H" else "")
				if (!bEspKevlar || bEspKevlarPos == "RIGHT") {
					boxDetailsLeftText.append("\n")
				}
			}

			if (bEspKevlar && bEspKevlarPos == "LEFT" && isPlayer) {
				boxDetailsLeftText.append(if (entityMemory.armor() > 0) "K\n" else "")
			}

			if (bEspScoped && bEspScopedPos == "LEFT" && isPlayer) {
				boxDetailsLeftText.append(if (entityMemory.isScoped()) "Scoped\n" else "")
			}

			if (bEspFlashed && bEspFlashedPos == "LEFT" && isPlayer) {
				boxDetailsLeftText.append(if (entityMemory.flashed()) "Flashed" else "")
			}

			//Draw possible right elements
			var rightShift = 1
			val boxDetailsRightText = StringBuilder()
			boxDetailsRightText.append("")

			if (bEspHealth && bEspHealthPos == "RIGHT" && isPlayer) {
				shapeRenderer.color = Color.BLACK
				shapeRenderer.rect(bbox.right + (barWidth * rightShift), bbox.top, barWidth, boxHeight)

				shapeRenderer.color = Color(1F - (.01F * health), (.01F * health), 0F, 1F)
				shapeRenderer.rect(bbox.right + (barWidth * rightShift), bbox.bottom, barWidth, -(boxHeight * (health/100F)))

				rightShift += 2
			}

			if (bEspArmor && bEspArmorPos == "RIGHT" && isPlayer) {
				shapeRenderer.color = Color.BLACK
				shapeRenderer.rect(bbox.right + (barWidth * rightShift), bbox.top, barWidth, boxHeight)

				shapeRenderer.color = Color(0F, .3F, 1F, 1F)
				shapeRenderer.rect(bbox.right + (barWidth * rightShift), bbox.bottom, barWidth, -(boxHeight * (entityMemory.armor()/100F)))

				rightShift += 2
			}

			shapeRenderer.end()

			if (bEspHelmet && bEspHelmetPos == "RIGHT" && isPlayer) {
				boxDetailsRightText.append(if (entityMemory.hasHelmet()) "H" else "")
				if (!bEspKevlar || bEspKevlarPos == "LEFT") {
					boxDetailsRightText.append("\n")
				}
			}

			if (bEspKevlar && bEspKevlarPos == "RIGHT" && isPlayer) {
				boxDetailsRightText.append(if (entityMemory.armor() > 0) "K\n" else "")
			}

			if (bEspScoped && bEspScopedPos == "RIGHT" && isPlayer) {
				boxDetailsRightText.append(if (entityMemory.isScoped()) "Scoped\n" else "")
			}

			if (bEspFlashed && bEspFlashedPos == "RIGHT" && isPlayer) {
				boxDetailsRightText.append(if (entityMemory.flashed()) "Flashed" else "")
			}

			//Draw possible top elements
			var topShift = 0
			val boxDetailsTopText = StringBuilder()
			boxDetailsTopText.append("")

			if (bEspName && bEspNamePos == "TOP") {
				if (isPlayer) {
					boxDetailsTopText.append("${ent.name()}\n")
				} else {
					boxDetailsTopText.append("${it.type.name.replace("CWeapon", "").toUpperCase().toLocale()}\n")
				}

				topShift += 18
			}

			if (bEspWeapon && bEspWeaponPos == "TOP" && isPlayer) {
				boxDetailsTopText.append(ent.weapon().name.toLocale())
				topShift += 18
			}

			if (bEspAmmo && bEspAmmoPos == "TOP") {
				val curAmmo = csgoEXE.int(ent + iClip1)
				val maxAmmo = csgoEXE.int(ent + iPrimaryReserveAmmoCount)

				if (curAmmo != -1 && maxAmmo > 0) {
					if (bEspWeapon) {
						if (bEspWeaponPos == "TOP") {
							boxDetailsTopText.append(" ")
						} else {
							topShift += 18
						}
					} else {
						boxDetailsTopText.append("\n")
						topShift += 18
					}
					boxDetailsTopText.append("[$curAmmo/$maxAmmo]")
				}
			}

			//Draw possible bottom elements
			val boxDetailsBottomText = StringBuilder()
			boxDetailsBottomText.append("")

			if (bEspName && bEspNamePos == "BOTTOM") {
				if (isPlayer) {
					boxDetailsBottomText.append("${ent.name()}\n")
				} else {
					boxDetailsBottomText.append("${it.type.name.replace("CWeapon", "").toUpperCase().toLocale()}\n")
				}
			}

			if (bEspWeapon && bEspWeaponPos == "BOTTOM" && isPlayer) {
				boxDetailsBottomText.append(ent.weapon().name.toLocale())
			}

			if (bEspAmmo && bEspAmmoPos == "BOTTOM") {
				val curAmmo = csgoEXE.int(ent + iClip1)
				val maxAmmo = csgoEXE.int(ent + iPrimaryReserveAmmoCount)

				if (curAmmo != -1 && maxAmmo > 0) {
					if (bEspWeapon) {
						if (bEspWeaponPos == "BOTTOM") {
							boxDetailsBottomText.append(" ")
						}
					} else {
						boxDetailsBottomText.append("\n")
					}
					boxDetailsBottomText.append("[$curAmmo/$maxAmmo]")
				}
			}

			sb.begin()

			val detailTextColor = curSettings["BOX_DETAILS_TEXT_COLOR"].strToColorGDX()
			val glyph = GlyphLayout()

			glyph.setText(textRenderer, boxDetailsLeftText, detailTextColor, 1F, Align.right, false)
			textRenderer.draw(sb, glyph, bbox.left - (barWidth * leftShift), bbox.top)

			glyph.setText(textRenderer, boxDetailsRightText, detailTextColor, 1F, Align.left, false)
			textRenderer.draw(sb, glyph, bbox.right + (barWidth * rightShift), bbox.top)

			glyph.setText(textRenderer, boxDetailsTopText, detailTextColor, 1F, Align.center, false)
			textRenderer.draw(sb, glyph, (bbox.left + bbox.right)/2F, bbox.top + topShift)

			glyph.setText(textRenderer, boxDetailsBottomText, detailTextColor, 1F, Align.center, false)
			textRenderer.draw(sb, glyph, (bbox.left + bbox.right)/2F, bbox.bottom)

			sb.end()
		}
	}
}

//Create a fake accurate box using headpos
fun setupFakeBox(ent: Entity): BoundingBox {
	val bbox = BoundingBox()

	val boneMemory = csgoEXE.read(ent.boneMatrix(), 3984)!!

	val headPos = Vector(boneMemory.getFloat(((0x30L * HEAD_BONE) + 0xC)).toDouble(),
			boneMemory.getFloat(((0x30L * HEAD_BONE) + 0x1C)).toDouble(),
			boneMemory.getFloat(((0x30L * HEAD_BONE) + 0x2C)).toDouble())

	val vHead = Vector(headPos.x, headPos.y, headPos.z + 9)
	val vFeet = Vector(vHead.x, vHead.y, vHead.z - 75)

	val vTop = Vector()
	val vBottom = Vector()

	if (worldToScreen(vHead, vTop) && worldToScreen(vFeet, vBottom)) {
		val vMiddle = Vector((vTop.x + vBottom.x)/2F, (vTop.y + vBottom.y)/2F, (vTop.z + vBottom.z)/2F)
		var boxH = vBottom.y - vTop.y
		val sW = abs(((boxH / 5.0) * 2.0) / 2.0)
		val sH = 2.0

		val midX = abs(abs(vTop.x) - abs(vBottom.x))
		if (abs(boxH) < sW + midX) {
			boxH = (sW + midX) * sign(boxH)
		}

		if (vBottom.x > vTop.x) {
			bbox.left = (vBottom.x + (sW * sign(vBottom.x))).toFloat()
			bbox.right = (vTop.x - (sW * sign(vTop.x))).toFloat()
		} else {
			bbox.left = (vTop.x + (sW * sign(vTop.x))).toFloat()
			bbox.right = (vBottom.x - (sW * sign(vBottom.x))).toFloat()
		}

		bbox.top = (vMiddle.y - boxH / 2.0 + sH).toFloat()
		bbox.bottom = (vMiddle.y + boxH / 2.0 + sH).toFloat()
	}

	return bbox
}

//Create a real accurate box using vecMins & vecMaxs
fun setupAccurateBox(ent: Entity): BoundingBox {
	//Get frameMatrix
	val frameMatrix = Array(4) { FloatArray(4) }
	val buffer = csgoEXE.read(ent + rgflCoordinateFrame - 0x30, 4 * 4 * 4)
	if (buffer != null) {
		if (buffer.getFloatArray(0, 16).all(Float::isFinite)) {
			var offset = 0
			for (row in 0..3) for (col in 0..3) {
				val value = buffer.getFloat(offset.toLong())
				frameMatrix[row][col] = value
				offset += 4 //Changed, error but not compd
			}
		}
	}

	val collisionMem: Memory by lazy {
		Memory(56)
	}
	csgoEXE.read(ent + m_Collision, collisionMem)

	//Set min/max
	val vecMins = Vector(collisionMem.getFloat(8).toDouble(), collisionMem.getFloat(12).toDouble(), collisionMem.getFloat(16).toDouble())
	val vecMaxs = Vector(collisionMem.getFloat(20).toDouble(), collisionMem.getFloat(24).toDouble(), collisionMem.getFloat(28).toDouble())

	//Set OBB to loop
	val pointsArray = mutableListOf<Vector>()
	pointsArray.add(Vector(vecMins.x, vecMins.y, vecMins.z))
	pointsArray.add(Vector(vecMins.x, vecMaxs.y, vecMins.z))
	pointsArray.add(Vector(vecMaxs.x, vecMaxs.y, vecMins.z))
	pointsArray.add(Vector(vecMaxs.x, vecMins.y, vecMins.z))
	pointsArray.add(Vector(vecMaxs.x, vecMaxs.y, vecMaxs.z))
	pointsArray.add(Vector(vecMins.x, vecMaxs.y, vecMaxs.z))
	pointsArray.add(Vector(vecMins.x, vecMins.y, vecMaxs.z))
	pointsArray.add(Vector(vecMaxs.x, vecMins.y, vecMaxs.z))

	val screenPointsTransformedArray = mutableListOf<Vector>()

	for (i in pointsArray) {
		val vecOut = Vector()
		worldToScreen(transformVector(i, frameMatrix), vecOut)
		screenPointsTransformedArray.add(vecOut)
	}

	var left = screenPointsTransformedArray[0].x
	var top = screenPointsTransformedArray[0].y
	var right = screenPointsTransformedArray[0].x
	var bottom = screenPointsTransformedArray[0].y

	for (i in screenPointsTransformedArray) {
		if (left > i.x) {
			left = i.x
		}

		if (top < i.y) {
			top = i.y
		}

		if (right < i.x) {
			right = i.x
		}

		if (bottom > i.y) {
			bottom = i.y
		}
	}

	return BoundingBox(left.toFloat(), right.toFloat(), top.toFloat(), bottom.toFloat())
}

fun transformVector(vec: Vector, array: Array<FloatArray>): Vector {
	val outVec = Vector()
	val thisVec = Vector3(vec.x.toFloat(), vec.y.toFloat(), vec.z.toFloat())
	outVec.x = (thisVec.dot(array[0][0], array[0][1], array[0][2]) + array[0][3]).toDouble()
	outVec.y = (thisVec.dot(array[1][0], array[1][1], array[1][2]) + array[1][3]).toDouble()
	outVec.z = (thisVec.dot(array[2][0], array[2][1], array[2][2]) + array[2][3]).toDouble()

	return outVec
}