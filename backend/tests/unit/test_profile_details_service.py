import pytest
from pydantic import ValidationError
from app.features.profile.schemas import PLAY_STYLE_VALUES, ProfileDetailsRequest


def test_play_style_values_contains_four_styles():
    assert len(PLAY_STYLE_VALUES) == 4
    assert "DEFENSIVE" in PLAY_STYLE_VALUES
    assert "OFFENSIVE" in PLAY_STYLE_VALUES
    assert "COUNTERPUNCHER" in PLAY_STYLE_VALUES
    assert "ALL_COURT" in PLAY_STYLE_VALUES


def test_all_valid_play_styles_accepted():
    for style in PLAY_STYLE_VALUES:
        req = ProfileDetailsRequest(play_style=style)
        assert req.play_style == style


def test_invalid_play_style_rejected():
    with pytest.raises(ValidationError):
        ProfileDetailsRequest(play_style="INVINCIBLE")


def test_null_play_style_accepted():
    req = ProfileDetailsRequest(play_style=None)
    assert req.play_style is None


def test_all_fields_null_accepted():
    req = ProfileDetailsRequest()
    assert req.play_style is None
    assert req.preferred_surfaces is None
    assert req.coach_instruction_1 is None
    assert req.coach_instruction_2 is None
    assert req.coach_instruction_3 is None


def test_coach_instructions_stored_independently():
    req = ProfileDetailsRequest(
        coach_instruction_1="Améliorer le service",
        coach_instruction_2="Travailler le revers",
        coach_instruction_3=None
    )
    assert req.coach_instruction_1 == "Améliorer le service"
    assert req.coach_instruction_2 == "Travailler le revers"
    assert req.coach_instruction_3 is None


def test_valid_surfaces_accepted():
    req = ProfileDetailsRequest(preferred_surfaces="CLAY,HARD")
    assert req.preferred_surfaces == "CLAY,HARD"


def test_all_surfaces_accepted():
    for surface in ["CLAY", "GRASS", "HARD", "CARPET"]:
        req = ProfileDetailsRequest(preferred_surfaces=surface)
        assert req.preferred_surfaces == surface


def test_invalid_surface_rejected():
    with pytest.raises(ValidationError):
        ProfileDetailsRequest(preferred_surfaces="CLAY,INVALID")


def test_preferred_surfaces_normalized_trims_spaces():
    req = ProfileDetailsRequest(preferred_surfaces="CLAY, HARD")
    assert req.preferred_surfaces == "CLAY,HARD"


def test_null_surfaces_accepted():
    req = ProfileDetailsRequest(preferred_surfaces=None)
    assert req.preferred_surfaces is None


def test_coach_instruction_max_length_enforced():
    with pytest.raises(ValidationError):
        ProfileDetailsRequest(coach_instruction_1="x" * 501)


def test_coach_instruction_at_max_length_accepted():
    req = ProfileDetailsRequest(coach_instruction_1="x" * 500)
    assert len(req.coach_instruction_1) == 500


def test_empty_string_preferred_surfaces_normalized_to_none():
    req = ProfileDetailsRequest(preferred_surfaces="")
    assert req.preferred_surfaces is None


def test_whitespace_only_preferred_surfaces_normalized_to_none():
    req = ProfileDetailsRequest(preferred_surfaces="  ,  , ")
    assert req.preferred_surfaces is None


def test_duplicate_surfaces_deduplicated():
    req = ProfileDetailsRequest(preferred_surfaces="CLAY,CLAY,HARD")
    assert req.preferred_surfaces == "CLAY,HARD"


def test_duplicate_surfaces_with_spaces_deduplicated():
    req = ProfileDetailsRequest(preferred_surfaces="CLAY, CLAY, HARD")
    assert req.preferred_surfaces == "CLAY,HARD"
