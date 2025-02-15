{{ config(
    indexes = [{'columns':['_airbyte_emitted_at'],'type':'hash'}],
    schema = "test_normalization",
    tags = [ "nested" ]
) }}
-- Final base SQL model
select
    _airbyte_partition_hashid,
    currency,
    _airbyte_ab_id,
    _airbyte_emitted_at,
    {{ current_timestamp() }} as _airbyte_normalized_at,
    _airbyte_data_hashid
from {{ ref('nested_stream_with_c___names_partition_data_ab3') }}
-- DATA at nested_stream_with_complex_columns_resulting_into_long_names/partition/DATA from {{ ref('nested_stream_with_c___long_names_partition') }}
where 1 = 1
{{ incremental_clause('_airbyte_emitted_at') }}

