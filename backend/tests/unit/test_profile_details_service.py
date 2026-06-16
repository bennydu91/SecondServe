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
