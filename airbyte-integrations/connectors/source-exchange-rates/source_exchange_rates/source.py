#
# Copyright (c) 2021 Airbyte, Inc., all rights reserved.
#


from typing import Any, Iterable, List, Mapping, MutableMapping, Optional, Tuple

import pendulum
import requests
from airbyte_cdk import AirbyteLogger
from airbyte_cdk.sources import AbstractSource
from airbyte_cdk.sources.streams import Stream
from airbyte_cdk.sources.streams.http import HttpStream
from pendulum import DateTime


class ExchangeRates(HttpStream):

    date_field_name = "date"

    # HttpStream related fields
    url_base = "http://api.exchangeratesapi.io/v1/"
    cursor_field = date_field_name
    primary_key = ""

    def __init__(self, base: Optional[str], start_date: DateTime, access_key: str):
        super().__init__()
        self._base = base
        self._start_date = start_date
        self.access_key = access_key

    def path(
        self, stream_state: Mapping[str, Any] = None, stream_slice: Mapping[str, Any] = None, next_page_token: Mapping[str, Any] = None
    ) -> str:
        return stream_slice[self.date_field_name]

    def next_page_token(self, response: requests.Response) -> Optional[Mapping[str, Any]]:
        return None

    def request_params(self, **kwargs) -> MutableMapping[str, Any]:
        params = {"access_key": self.access_key}

        if self._base is not None:
            params["base"] = self._base

        return params

    def parse_response(self, response: requests.Response, **kwargs) -> Iterable[Mapping]:
        response_json = response.json()
        yield response_json

    def stream_slices(self, stream_state: Mapping[str, Any] = None, **kwargs) -> Iterable[Optional[Mapping[str, Any]]]:
        stream_state = stream_state or {}
        start_date = pendulum.parse(stream_state.get(self.date_field_name, self._start_date))
        return chunk_date_range(start_date)

    def get_updated_state(self, current_stream_state: MutableMapping[str, Any], latest_record: Mapping[str, Any]):
        current_stream_state = current_stream_state or {}
        current_stream_state[self.date_field_name] = max(
            latest_record[self.date_field_name], current_stream_state.get(self.date_field_name, self._start_date)
        )
        return current_stream_state


def chunk_date_range(start_date: DateTime) -> Iterable[Mapping[str, Any]]:
    """
    Returns a list of each day between the start date and now. Ignore weekends since exchanges don't run on weekends.
    The return value is a list of dicts {'date': date_string}.
    """
    days = []
    now = pendulum.now()
    while start_date < now:
        day_of_week = start_date.day_of_week
        if day_of_week != pendulum.SATURDAY and day_of_week != pendulum.SUNDAY:
            days.append({"date": start_date.to_date_string()})
        start_date = start_date.add(days=1)

    return days


class SourceExchangeRates(AbstractSource):
    def check_connection(self, logger: AirbyteLogger, config: Mapping[str, Any]) -> Tuple[bool, Any]:
        try:
            params = {"access_key": config["access_key"]}
            base = config.get("base")
            if base is not None:
                params["base"] = base

            resp = requests.get(f"{ExchangeRates.url_base}{config['start_date']}", params=params)
            status = resp.status_code
            logger.info(f"Ping response code: {status}")
            if status == 200:
                return True, None
            # When API requests is sent but the requested data is not available or the API call fails
            # for some reason, a JSON error is returned.
            # https://exchangeratesapi.io/documentation/#errors
            error = resp.json().get("error")
            code = error.get("code")
            message = error.get("message") or error.get("info")
            # If code is base_currency_access_restricted, error is caused by switching base currency while using free
            # plan
            if code == "base_currency_access_restricted":
                message = f"{message} (this plan doesn't support selecting the base currency)"
            return False, message
        except Exception as e:
            return False, e

    def streams(self, config: Mapping[str, Any]) -> List[Stream]:
        return [ExchangeRates(config.get("base"), config["start_date"], config["access_key"])]
