import pytest
from pydantic import ValidationError
from app.features.profile.schemas import FFT_VALID_SERIES, RankingRequest


def test_valid_series_list_has_15_entries():
    assert len(FFT_VALID_SERIES) == 15


def test_all_valid_series_accepted():
    for series in FFT_VALID_SERIES:
        req = RankingRequest(series=series, points=100)
        assert req.series == series


def test_invalid_series_rejected():
    with pytest.raises(ValidationError):
        RankingRequest(series="40/5", points=100)


def test_invalid_series_text_rejected():
    with pytest.raises(ValidationError):
        RankingRequest(series="invalide", points=100)


def test_zero_points_rejected():
    with pytest.raises(ValidationError):
        RankingRequest(series="15/2", points=0)


def test_negative_points_rejected():
    with pytest.raises(ValidationError):
        RankingRequest(series="15/2", points=-50)


def test_valid_request_accepted():
    req = RankingRequest(series="15/2", points=850)
    assert req.series == "15/2"
    assert req.points == 850
